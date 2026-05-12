package com.demo.service;

import com.demo.common.AppConstants;
import com.demo.memory.ChatMessage;
import com.smartlibrary.common.Result;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private final BookService bookService;
    private final ChatModel chatModel;
    private final VectorStoreService vectorStoreService;
    private final ChatMemoryService chatMemoryService;
    private final LibraryAnalyticsAnswerService libraryAnalyticsAnswerService;

    @PostConstruct
    public void initVectorStore() {
        try {
            vectorStoreService.ingest(bookService.list());
            log.info("Vector store initialized with books from database.");
        } catch (Exception e) {
            log.warn("Vector store initialization failed, AI will degrade gracefully.", e);
        }
    }

    public Result<String> analyze(String question) {
        return analyze(question, false);
    }

    public Result<String> analyze(String question, boolean debug) {
        if (question == null || question.isBlank()) {
            return Result.fail(400, "question 不能为空");
        }

        String cleanQuestion = question.trim();
        List<ChatMessage> memoryHistory = chatMemoryService.loadAndCompressHistory(AppConstants.DEMO_SESSION_ID);
        String memoryContext = chatMemoryService.buildMemoryContext(memoryHistory);

        Optional<String> analyticsAnswer = libraryAnalyticsAnswerService.tryAnswer(cleanQuestion);
        if (analyticsAnswer.isPresent()) {
            String a = sanitizeLeakedReasoning(analyticsAnswer.get());
            if (!a.isEmpty()) {
                String out = ensureAnalyticsSourceTag(a);
                chatMemoryService.saveTurnSafelyAsync(AppConstants.DEMO_SESSION_ID, cleanQuestion, out);
                return Result.ok(out);
            }
        }

        // 用户显式使用 /查询 /query 时，表示强制走 NL2SQL；不应再回退到 RAG 产生“隐私拒绝”等误导话术
        String lower = cleanQuestion.toLowerCase(Locale.ROOT);
        if (cleanQuestion.startsWith(AppConstants.FORCE_ANALYTICS_PREFIX_CN)
                || lower.startsWith(AppConstants.FORCE_ANALYTICS_PREFIX_EN)) {
            return Result.ok("统计分析链路未返回结果：请检查服务日志（是否成功调用到统计助手与 queryLibraryDatabase），以及数据库是否可连接。");
        }

        boolean factIntent = isFactIntent(cleanQuestion);
        boolean chitchatIntent = isChitchatIntent(cleanQuestion);
        boolean likelyBookIntent = isLikelyBookIntent(cleanQuestion);

        List<VectorStoreService.SearchHit> searchHits = List.of();
        // 只有“纯闲聊”（命中闲聊但不含任何图书/事实检索意图）才跳过检索；
        // 情绪表达 + 找书/问作者/问库存 等混合问题，也应检索以减少幻觉。
        boolean pureChitchat = chitchatIntent && !likelyBookIntent && !factIntent;
        boolean shouldSearchRag = !pureChitchat && (likelyBookIntent || factIntent);
        if (shouldSearchRag) {
            try {
                searchHits = vectorStoreService.hybridSearch(cleanQuestion, AppConstants.RAG_HYBRID_TOP_K, debug);
            } catch (Exception e) {
                log.warn("Vector search failed, fallback without RAG context.", e);
            }
        }

        boolean hasStrongContext = hasStrongContext(searchHits);
        boolean useCatalogContext = shouldSearchRag && hasStrongContext;
        String ragContext = useCatalogContext ? buildRagContext(searchHits) : "（本次未使用馆藏检索上下文）";
        String ragPolicy = useCatalogContext
                ? "本次检索结果可信，请优先依据 [RAG 馆藏背景] 作答。"
                : "本次检索结果弱相关或用户偏闲聊，请降低对 [RAG 馆藏背景] 的依赖，自然回答即可。";

        String prompt = """
                [系统指令]
                %s
                请先判断用户意图，再决定是否使用 [RAG 馆藏背景]：
                - 意图 A（推荐/分析类图书咨询）
                - 意图 B（事实细节类图书咨询）
                - 意图 C（日常社交/通用咨询）
                如果用户只是打招呼、情绪表达或闲聊，可跳过馆藏检索，直接自然交流。
                %s

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
                4.1) 严禁编造馆藏：当用户问“有没有/是否有某作者/某书名/某标签的书”“馆里有吗”等可被视为馆藏存在性判断的问题时，
                     只有在 [RAG 馆藏背景] 或 [历史记忆] 中出现了对应作者/书名/证据，才允许断言“馆内有/有收录/有库存”；
                     若未出现证据，必须明确回答“本次馆藏检索未命中”，并从已检索到的相关书中给出替代建议，或引导用户使用 `/查询` 做精确检索。
                5) 涉及数量时严格区分口径，勿混用「本 / 种 / 册 / 分类」：
                   - 「种」「书目」：馆藏里有多少个不同的书目条目（一种书一条记录）；不要说成「本」若实际指条目数。
                   - 「册」：库存/复本总量（若上下文或字段能体现 stock 再谈册数；RAG 片段未给 stock 时不要编造总册数）。
                   - 「分类」：category 维度，与「多少种书」不是同一概念。
                   - 用户只问「多少本书」且背景只有书目条数时，优先答「共 N 种书目」或「N 条馆藏记录」，并说明若需总册数需按库存汇总。

                [RAG 馆藏背景]
                %s

                [历史记忆]
                %s

                [当前用户问题]
                %s
                """.formatted(AppConstants.SYSTEM_PERSONA, ragPolicy, ragContext, memoryContext, cleanQuestion);

        try {
            String answer = chatModel.chat(prompt);
            answer = postProcessAnswer(cleanQuestion, answer, useCatalogContext, factIntent, chitchatIntent, likelyBookIntent);
            chatMemoryService.saveTurnSafelyAsync(AppConstants.DEMO_SESSION_ID, cleanQuestion, answer);
            return Result.ok(answer);
        } catch (Exception e) {
            log.warn("chatModel.chat 调用失败，将返回降级答复: {}", e.toString(), e);
            String fallback = """
                    我这边刚刚连接外部大模型服务时遇到了一点波动，暂时没能稳定生成回答。

                    你可以：
                    - 过几秒再试一次
                    - 或者把问题换一种说法（更具体一点）

                    如果你现在想问馆藏/借阅/库存等统计问题，也可以用 `/查询` 开头，我会走数据库统计链路更稳定地帮你查。
                    """.trim();
            // 降级答复也保存，保证对话不断档
            chatMemoryService.saveTurnSafelyAsync(AppConstants.DEMO_SESSION_ID, cleanQuestion, fallback);
            return Result.ok(fallback);
        }
    }

    private String postProcessAnswer(String question, String rawAnswer, boolean useCatalogContext,
                                     boolean factIntent, boolean chitchatIntent, boolean likelyBookIntent) {
        String answer = sanitizeLeakedReasoning(rawAnswer);
        if (answer.isEmpty()) {
            return "我还不太确定你的具体需求。你可以告诉我想看的书名、作者或分类，我可以继续帮你。";
        }
        if (factIntent) {
            answer = removeTemplateHeadings(answer);
        }

        if (chitchatIntent && !likelyBookIntent) {
            return answer;
        }

        return ensureSourceTagIfBookAnswer(answer, question, useCatalogContext);
    }

    private String ensureAnalyticsSourceTag(String answer) {
        if (answer.contains(AppConstants.SOURCE_TAG)) {
            return answer;
        }
        return answer + " " + AppConstants.SOURCE_TAG;
    }

    private boolean isFactIntent(String question) {
        return containsAnyKeyword(question, AppConstants.RAG_FACT_INTENT_KEYWORDS);
    }

    private boolean isChitchatIntent(String question) {
        return containsAnyKeyword(question, AppConstants.RAG_CHITCHAT_INTENT_KEYWORDS);
    }

    private boolean isLikelyBookIntent(String question) {
        return containsAnyKeyword(question, AppConstants.RAG_LIKELY_BOOK_INTENT_KEYWORDS);
    }

    private static boolean containsAnyKeyword(String question, List<String> keywords) {
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (q.contains(keyword)) {
                return true;
            }
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

    private String ensureSourceTagIfBookAnswer(String answer, String question, boolean useCatalogContext) {
        if (!useCatalogContext) return answer;
        if (!isLikelyBookIntent(question)) return answer;
        if (answer.contains(AppConstants.SOURCE_TAG)) return answer;
        return answer + " " + AppConstants.SOURCE_TAG;
    }

    private String sanitizeLeakedReasoning(String rawAnswer) {
        String answer = rawAnswer == null ? "" : rawAnswer.trim();
        if (answer.isEmpty()) {
            return "";
        }

        // Prefer preserving the main user-facing answer and cutting off trailing reasoning/log artifacts.
        for (String marker : AppConstants.RAG_OUTPUT_CUT_MARKERS) {
            int idx = answer.indexOf(marker);
            if (idx > 0) {
                answer = answer.substring(0, idx).trim();
            }
        }

        // Remove common chain-of-thought wrappers if the model accidentally returns them.
        answer = answer
                .replace("<think>", "")
                .replace("</think>", "")
                .replace("```json", "")
                .replace("```", "")
                .trim();

        return answer;
    }

    private boolean hasStrongContext(List<VectorStoreService.SearchHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return false;
        }
        return hits.stream().anyMatch(hit -> hit.score() >= AppConstants.MIN_VECTOR_SCORE);
    }

    private String buildRagContext(List<VectorStoreService.SearchHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return "（检索结果为空）";
        }
        StringBuilder sb = new StringBuilder();
        for (VectorStoreService.SearchHit hit : hits) {
            sb.append("- score=")
                    .append(String.format(Locale.ROOT, "%.4f", hit.score()))
                    .append("; segment=")
                    .append(hit.text())
                    .append('\n');
        }
        return sb.toString().trim();
    }
}

