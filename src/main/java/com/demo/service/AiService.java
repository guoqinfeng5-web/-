package com.demo.service;

import com.demo.entity.Book;
import com.demo.memory.ChatMessage;
import com.smartlibrary.common.Result;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AiService {

    private static final String DEMO_SESSION_ID = "demo_user_001";
    private static final String HISTORY_KEY_PREFIX = "ai:chat:history:v2:";
    private static final int MAX_HISTORY_MESSAGES = 10;
    private static final String SOURCE_TAG = "[数据来源：馆藏库]";
    private static final String SYSTEM_PERSONA = "你是“智能图书管员”，首要任务是帮用户找书，但你也是一个博学、温暖的伙伴。";

    private final BookService bookService;
    private final ChatLanguageModel chatLanguageModel;
    private final RedisTemplate<String, Object> redisTemplate;
    private final LlmSummaryService llmSummaryService;

    public Result<String> analyze(String question) {
        if (question == null || question.isBlank()) {
            return Result.fail(400, "question 不能为空");
        }

        String cleanQuestion = question.trim();
        String historyKey = HISTORY_KEY_PREFIX + DEMO_SESSION_ID;
        List<ChatMessage> memoryHistory = loadHistorySafely(historyKey);
        memoryHistory = compressHistoryIfNeeded(historyKey, memoryHistory);

        List<Book> books = bookService.list();
        String ragContext = buildContext(books);
        String memoryContext = buildMemoryContext(memoryHistory);

        String prompt = """
                [系统指令]
                %s
                请先判断用户意图，再决定是否使用 [RAG 馆藏背景]：
                - 意图 A（推荐/分析类图书咨询）
                - 意图 B（事实细节类图书咨询）
                - 意图 C（日常社交/通用咨询）
                如果用户只是打招呼、情绪表达或闲聊，可跳过馆藏检索，直接自然交流。

                以下是目前数据库中的可用书籍信息：
                %s

                用户提问：‘%s’

                【输出格式动态指令】
                请先判断用户提问意图，再选择输出风格：

                👉 意图 A（泛咨询 / 求推荐 / 深度分析）
                例如：推荐 Java 书籍、这本书怎么样、给我学习路径。
                你必须使用以下 Markdown 结构：
                ### 🎯 核心分析
                #### 💡 推荐理由
                #### 👤 适合人群
                建议 180~320 字，逻辑清晰，有结论有依据，语气专业且亲和。

                👉 意图 B（事实性细节问答）
                例如：这本书多少钱、作者是谁、库存多少、借阅次数多少、在哪借。
                你必须直接简洁回答，建议 60~140 字。
                绝对不要使用“### 🎯 核心分析 / #### 💡 推荐理由 / #### 👤 适合人群”这三个标题。
                可用自然段或简短要点列表。

                👉 意图 C（日常社交/通用咨询）
                例如：你好、你是谁、谢谢、随便聊聊、今天心情不好。
                以朋友或资深馆员身份自然回复，字数不限，语气亲和温暖，不强制使用图书模板。
                若用户同时包含图书问题与日常关怀诉求，允许混合回答：先答图书，再给关怀建议。

                【全局约束】
                1) 仅输出 Markdown，不要输出 JSON。
                2) 图书相关回答优先参考 [RAG 馆藏背景] 与 [历史记忆]；若用户聊非图书话题，可使用通用知识自然作答。
                3) 如确实使用了馆藏数据，书名或数据点后追加“[数据来源：馆藏库]”。
                4) 不要机械回复“馆藏信息不足”，应给用户下一步可执行引导。

                [RAG 馆藏背景]
                %s

                [历史记忆]
                %s

                [当前用户问题]
                %s
                """.formatted(SYSTEM_PERSONA, ragContext, cleanQuestion, ragContext, memoryContext, cleanQuestion);

        String answer = chatLanguageModel.generate(prompt);
        answer = postProcessAnswer(cleanQuestion, answer);
        saveTurnSafely(historyKey, cleanQuestion, answer);
        return Result.ok(answer);
    }

    private String postProcessAnswer(String question, String rawAnswer) {
        String answer = rawAnswer == null ? "" : rawAnswer.trim();
        if (answer.isEmpty()) {
            return "我还不太确定你的具体需求。你可以告诉我想看的书名、作者或分类，我可以继续帮你。";
        }

        boolean factIntent = isFactIntent(question);
        boolean chitchatIntent = isChitchatIntent(question);
        boolean likelyBookIntent = isLikelyBookIntent(question);
        if (factIntent) {
            answer = removeTemplateHeadings(answer);
        }

        if (chitchatIntent && !likelyBookIntent) {
            return answer;
        }

        return ensureSourceTagIfBookAnswer(answer, question);
    }

    private boolean isFactIntent(String question) {
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
        String[] keywords = {
                "价格", "多少钱", "价位", "作者", "出版", "isbn", "库存", "借阅", "借了多少", "borrow",
                "score", "评分", "分类", "在哪", "位置", "馆藏", "有几本", "数量"
        };
        for (String keyword : keywords) {
            if (q.contains(keyword)) return true;
        }
        return false;
    }

    private boolean isChitchatIntent(String question) {
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
        String[] keywords = {
                "你好", "嗨", "hello", "hi", "你是谁", "谢谢", "感谢", "在吗", "早上好", "晚上好",
                "心情不好", "难过", "焦虑", "聊聊", "随便聊", "无聊", "最近怎么样"
        };
        for (String keyword : keywords) {
            if (q.contains(keyword)) return true;
        }
        return false;
    }

    private boolean isLikelyBookIntent(String question) {
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
        String[] keywords = {
                "书", "图书", "推荐", "作者", "分类", "评分", "借阅", "库存", "馆藏",
                "price", "borrow", "isbn", "title", "book"
        };
        for (String keyword : keywords) {
            if (q.contains(keyword)) return true;
        }
        return false;
    }

    private String removeTemplateHeadings(String answer) {
        return answer
                .replace("### 🎯 核心分析", "")
                .replace("#### 💡 推荐理由", "")
                .replace("#### 👤 适合人群", "")
                .trim();
    }

    private String ensureSourceTagIfBookAnswer(String answer, String question) {
        if (!isLikelyBookIntent(question)) return answer;
        if (answer.contains(SOURCE_TAG)) return answer;
        return answer + " " + SOURCE_TAG;
    }

    private List<ChatMessage> loadHistorySafely(String historyKey) {
        try {
            List<Object> raw = redisTemplate.opsForList().range(historyKey, 0, -1);
            if (raw == null || raw.isEmpty()) return new ArrayList<>();

            List<ChatMessage> result = new ArrayList<>(raw.size());
            for (Object obj : raw) {
                ChatMessage msg = convertToChatMessage(obj);
                if (msg != null) result.add(msg);
            }
            return result;
        } catch (Exception ignored) {
            // Redis 不可用时降级为无记忆模式
            return new ArrayList<>();
        }
    }

    private List<ChatMessage> compressHistoryIfNeeded(String historyKey, List<ChatMessage> history) {
        try {
            if (history.size() <= MAX_HISTORY_MESSAGES || history.size() < 2) {
                return history;
            }

            int half = history.size() / 2;
            if (half <= 0) return history;

            List<ChatMessage> firstHalf = history.subList(0, half);
            String summary = llmSummaryService.generateSummary(firstHalf);
            ChatMessage summaryMessage = new ChatMessage();
            summaryMessage.setRole("system");
            summaryMessage.setContent("SUMMARY: " + (summary == null ? "" : summary.trim()));
            summaryMessage.setTimestamp(System.currentTimeMillis());

            redisTemplate.opsForList().trim(historyKey, half, -1);
            redisTemplate.opsForList().leftPush(historyKey, summaryMessage);

            List<Object> refreshed = redisTemplate.opsForList().range(historyKey, 0, -1);
            if (refreshed == null || refreshed.isEmpty()) {
                return Collections.singletonList(summaryMessage);
            }

            List<ChatMessage> result = new ArrayList<>(refreshed.size());
            for (Object obj : refreshed) {
                ChatMessage msg = convertToChatMessage(obj);
                if (msg != null) result.add(msg);
            }
            return result;
        } catch (Exception ignored) {
            // Redis 或摘要过程失败时，保持原有历史继续工作
            return history;
        }
    }

    private String buildMemoryContext(List<ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return "（暂无历史记忆）";
        }
        StringBuilder sb = new StringBuilder();
        for (ChatMessage msg : history) {
            if (msg == null) continue;
            sb.append(msg.getRole()).append("：").append(msg.getContent()).append("\n");
        }
        return sb.toString().trim();
    }

    private void saveTurnSafely(String historyKey, String userQuestion, String aiAnswer) {
        try {
            ChatMessage userMsg = new ChatMessage();
            userMsg.setRole("user");
            userMsg.setContent(userQuestion);
            userMsg.setTimestamp(System.currentTimeMillis());

            ChatMessage aiMsg = new ChatMessage();
            aiMsg.setRole("assistant");
            aiMsg.setContent(aiAnswer);
            aiMsg.setTimestamp(System.currentTimeMillis());

            redisTemplate.opsForList().rightPush(historyKey, userMsg);
            redisTemplate.opsForList().rightPush(historyKey, aiMsg);
        } catch (Exception ignored) {
            // Redis 不可用时降级为无记忆模式
        }
    }

    private ChatMessage convertToChatMessage(Object obj) {
        if (obj == null) return null;
        if (obj instanceof ChatMessage) return (ChatMessage) obj;

        if (obj instanceof LinkedHashMap<?, ?> map) {
            ChatMessage msg = new ChatMessage();
            Object role = map.get("role");
            Object content = map.get("content");
            Object timestamp = map.get("timestamp");
            msg.setRole(role == null ? "assistant" : role.toString());
            msg.setContent(content == null ? "" : content.toString());
            msg.setTimestamp(parseLong(timestamp));
            return msg;
        }

        ChatMessage fallback = new ChatMessage();
        fallback.setRole("assistant");
        fallback.setContent(obj.toString());
        fallback.setTimestamp(System.currentTimeMillis());
        return fallback;
    }

    private long parseLong(Object value) {
        if (value == null) return System.currentTimeMillis();
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (Exception ignored) {
            return System.currentTimeMillis();
        }
    }

    private String buildContext(List<Book> books) {
        if (books == null || books.isEmpty()) {
            return "（当前数据库暂无书籍数据）";
        }

        int maxBooks = 80;
        int maxSummaryChars = 400;
        int maxContextChars = 12000;

        StringBuilder sb = new StringBuilder(Math.min(maxContextChars, 4096));
        int count = 0;

        for (Book b : books) {
            if (b == null) continue;
            if (count >= maxBooks) break;
            if (sb.length() >= maxContextChars) break;

            String title = safeOneLine(b.getTitle());
            String author = safeOneLine(b.getAuthor());
            String category = safeOneLine(b.getCategory());
            String summary = safeOneLine(b.getSummary());
            if (summary.length() > maxSummaryChars) {
                summary = summary.substring(0, maxSummaryChars) + "...";
            }

            sb.append("- title: ").append(title)
              .append("; author: ").append(author)
              .append("; category: ").append(category)
              .append("; summary: ").append(summary)
              .append('\n');

            count++;
        }

        if (sb.length() > maxContextChars) {
            sb.setLength(maxContextChars);
        }

        return sb.toString().trim();
    }

    private String safeOneLine(String s) {
        if (s == null) return "";
        return s.replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .trim();
    }
}

