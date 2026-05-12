package com.demo.service;

import com.demo.entity.Book;
import com.smartlibrary.common.Result;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
class AiServiceMemorySummaryTest {

    private static final String HISTORY_KEY = "ai:chat:history:v2:demo_user_001";

    @Autowired
    private AiService aiService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @MockBean
    private ChatModel chatModel;

    @MockBean
    private BookService bookService;

    @MockBean
    private VectorStoreService vectorStoreService;

    @MockBean
    private LibraryAnalyticsAnswerService libraryAnalyticsAnswerService;

    @BeforeEach
    void setUp() {
        redisTemplate.delete(HISTORY_KEY);

        when(bookService.list()).thenReturn(List.of(
                Book.builder().id(1L).title("Java 核心技术").author("A").category("Java").summary("Java 经典入门").build(),
                Book.builder().id(2L).title("数据库系统概论").author("B").category("DB").summary("数据库基础教材").build()
        ));

        when(chatModel.chat(anyString())).thenAnswer(invocation -> {
            String prompt = invocation.getArgument(0, String.class);
            if (prompt.contains("精简摘要")) {
                return "这是历史对话摘要，用户偏好 Java 与数据库方向。";
            }
            return "这是用于测试的较长回答内容，用于推动历史长度超过阈值并触发滑动窗口摘要机制。[数据来源：馆藏库]";
        });

        when(vectorStoreService.search(anyString(), anyInt())).thenReturn(List.of(
                new VectorStoreService.SearchHit(0, "title: Java 核心技术; summary: Java 经典入门", 0.82)
        ));

        when(libraryAnalyticsAnswerService.tryAnswer(anyString())).thenReturn(Optional.empty());
    }

    @Test
    void shouldGenerateSummaryWhenHistoryExceedsThreshold() {
        for (int i = 0; i < 8; i++) {
            Result<String> result = aiService.analyze("第" + i + "轮：请推荐并分析 Java 与数据库相关书籍，顺便比较价格与库存。");
            assertEquals(200, result.getCode());
            assertNotNull(result.getData());
            sleep(120);
        }

        List<Object> history = waitForHistory(HISTORY_KEY, 30, 150);
        assertNotNull(history);
        assertFalse(history.isEmpty(), "历史记录不应为空");

        boolean hasSummary = history.stream()
                .map(this::extractContent)
                .anyMatch(s -> s.startsWith("SUMMARY:"));
        assertTrue(hasSummary, "应出现 SUMMARY: 前缀的摘要记录，说明压缩生效");
    }

    private List<Object> waitForHistory(String key, int attempts, long sleepMillis) {
        for (int i = 0; i < attempts; i++) {
            List<Object> history = redisTemplate.opsForList().range(key, 0, -1);
            if (history != null && !history.isEmpty()) {
                return history;
            }
            sleep(sleepMillis);
        }
        return redisTemplate.opsForList().range(key, 0, -1);
    }

    private void sleep(long millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String extractContent(Object obj) {
        if (obj instanceof com.demo.memory.ChatMessage msg) {
            return msg.getContent();
        }
        if (obj instanceof LinkedHashMap<?, ?> map) {
            Object content = map.get("content");
            return content == null ? "" : content.toString();
        }
        return obj == null ? "" : obj.toString();
    }
}

