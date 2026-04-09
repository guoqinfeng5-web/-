package com.demo.service;

import com.demo.entity.Book;
import com.demo.memory.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
class AiServiceSummaryLiveKeyTest {

    private static final String LIVE_KEY = "ai:chat:history:v2:demo_user_001";

    @Autowired
    private AiService aiService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @MockBean
    private ChatLanguageModel chatLanguageModel;

    @MockBean
    private BookService bookService;

    @Test
    void shouldGenerateSummaryForLiveKeyWhenHistoryExceedsLimit() {
        when(bookService.list()).thenReturn(List.of(
                Book.builder().id(1L).title("测试书").author("测试作者").category("测试分类").summary("测试摘要").build()
        ));

        when(chatLanguageModel.generate(anyString())).thenAnswer(invocation -> {
            String prompt = invocation.getArgument(0, String.class);
            if (prompt.contains("精简摘要")) {
                return "这是压缩后的摘要内容";
            }
            return "这是普通回答内容 [数据来源：馆藏库]";
        });

        List<Object> before = redisTemplate.opsForList().range(LIVE_KEY, 0, -1);
        int beforeSize = before == null ? 0 : before.size();

        aiService.analyze("触发一次摘要检查");

        List<Object> after = redisTemplate.opsForList().range(LIVE_KEY, 0, -1);
        int afterSize = after == null ? 0 : after.size();
        boolean hasSummary = after != null && after.stream()
                .map(this::extractContent)
                .anyMatch(s -> s != null && s.startsWith("SUMMARY:"));

        String sample = after == null ? "null" : after.stream()
                .map(this::extractContent)
                .limit(5)
                .toList()
                .toString();
        assertTrue(hasSummary, "未检测到 SUMMARY:，beforeSize=" + beforeSize + ", afterSize=" + afterSize + ", sample=" + sample);
    }

    private String extractContent(Object obj) {
        if (obj instanceof ChatMessage msg) {
            return msg.getContent();
        }
        if (obj instanceof LinkedHashMap<?, ?> map) {
            Object content = map.get("content");
            return content == null ? null : content.toString();
        }
        return obj == null ? null : obj.toString();
    }
}

