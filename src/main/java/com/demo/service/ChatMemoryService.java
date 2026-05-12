package com.demo.service;

import com.demo.common.AppConstants;
import com.demo.memory.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMemoryService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final LlmSummaryService llmSummaryService;

    public List<ChatMessage> loadAndCompressHistory(String sessionId) {
        String historyKey = AppConstants.HISTORY_KEY_PREFIX + sessionId;
        List<ChatMessage> history = loadHistorySafely(historyKey);
        return compressHistoryIfNeeded(historyKey, history);
    }

    public void saveTurnSafelyAsync(String sessionId, String userQuestion, String aiAnswer) {
        CompletableFuture.runAsync(() -> saveTurnSafely(sessionId, userQuestion, aiAnswer));
    }

    public String buildMemoryContext(List<ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return "（暂无历史记忆）";
        }
        StringBuilder sb = new StringBuilder();
        for (ChatMessage msg : history) {
            if (msg == null) {
                continue;
            }
            sb.append(msg.getRole()).append("：").append(msg.getContent()).append("\n");
        }
        return sb.toString().trim();
    }

    private List<ChatMessage> loadHistorySafely(String historyKey) {
        try {
            List<Object> raw = redisTemplate.opsForList().range(historyKey, 0, -1);
            if (raw == null || raw.isEmpty()) {
                return new ArrayList<>();
            }

            List<ChatMessage> result = new ArrayList<>(raw.size());
            for (Object obj : raw) {
                ChatMessage msg = convertToChatMessage(obj);
                if (msg != null) {
                    result.add(msg);
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("Load chat history failed, fallback to stateless mode. key={}", historyKey, e);
            return new ArrayList<>();
        }
    }

    private List<ChatMessage> compressHistoryIfNeeded(String historyKey, List<ChatMessage> history) {
        try {
            if (history.size() <= AppConstants.MAX_HISTORY_MESSAGES || history.size() < 2) {
                return history;
            }

            int half = history.size() / 2;
            if (half <= 0) {
                return history;
            }

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
                if (msg != null) {
                    result.add(msg);
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("Compress chat history failed, keep original history. key={}", historyKey, e);
            return history;
        }
    }

    private void saveTurnSafely(String sessionId, String userQuestion, String aiAnswer) {
        String historyKey = AppConstants.HISTORY_KEY_PREFIX + sessionId;
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
        } catch (Exception e) {
            log.warn("Save chat history failed, fallback to stateless mode. key={}", historyKey, e);
        }
    }

    private ChatMessage convertToChatMessage(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof ChatMessage) {
            return (ChatMessage) obj;
        }
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
        if (value == null) {
            return System.currentTimeMillis();
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (Exception ignored) {
            return System.currentTimeMillis();
        }
    }
}

