package com.demo.service;

import com.demo.ai.analytics.LibraryAnalyticsAssistant;
import com.demo.ai.intent.StructuredAnalyticsIntentDetector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("馆藏统计分析应答编排单元测试")
@ExtendWith(MockitoExtension.class)
class LibraryAnalyticsAnswerServiceTest {

    @Mock
    private LibraryAnalyticsAssistant libraryAnalyticsAssistant;

    @Mock
    private StructuredAnalyticsIntentDetector structuredAnalyticsIntentDetector;

    @InjectMocks
    private LibraryAnalyticsAnswerService libraryAnalyticsAnswerService;

    @Test
    @DisplayName("问题为空 - 返回 empty")
    void testTryAnswer_BlankQuestion_ReturnsEmpty() {
        // When
        Optional<String> r1 = libraryAnalyticsAnswerService.tryAnswer(null);
        Optional<String> r2 = libraryAnalyticsAnswerService.tryAnswer("   ");

        // Then
        assertTrue(r1.isEmpty());
        assertTrue(r2.isEmpty());
        verify(libraryAnalyticsAssistant, never()).answer(any());
    }

    @Test
    @DisplayName("非强制且未命中统计意图 - 不调助手")
    void testTryAnswer_NotForced_NotAnalytics_ReturnsEmpty() {
        // Given
        when(structuredAnalyticsIntentDetector.isAnalyticsQuestion("推荐一本Java书")).thenReturn(false);

        // When
        Optional<String> result = libraryAnalyticsAnswerService.tryAnswer("推荐一本Java书");

        // Then
        assertTrue(result.isEmpty());
        verify(libraryAnalyticsAssistant, never()).answer(any());
    }

    @Test
    @DisplayName("仅前缀无正文 - 返回 empty")
    void testTryAnswer_ForcedPrefixOnly_ReturnsEmpty() {
        // When：strip 后 actualQuestion 为空，不会调用 detector 与 assistant
        Optional<String> cn = libraryAnalyticsAnswerService.tryAnswer("/查询");
        Optional<String> en = libraryAnalyticsAnswerService.tryAnswer("/query");

        // Then
        assertTrue(cn.isEmpty());
        assertTrue(en.isEmpty());
        verify(structuredAnalyticsIntentDetector, never()).isAnalyticsQuestion(anyString());
        verify(libraryAnalyticsAssistant, never()).answer(any());
    }

    @Test
    @DisplayName("强制查询且助手返回答案 - 返回 trimmed 文本")
    void testTryAnswer_ForcedWithBody_AssistantOk_ReturnsAnswer() {
        // Given
        when(libraryAnalyticsAssistant.answer("按分类统计种数"))
                .thenReturn("  统计结果Markdown  ");

        // When
        Optional<String> result = libraryAnalyticsAnswerService.tryAnswer("/查询 按分类统计种数");

        // Then
        assertTrue(result.isPresent());
        assertEquals("统计结果Markdown", result.get());
    }

    @Test
    @DisplayName("强制查询但助手返回空 - 返回错误说明")
    void testTryAnswer_Forced_AssistantBlank_ReturnsErrorHint() {
        // Given
        when(libraryAnalyticsAssistant.answer("x")).thenReturn("");

        // When
        Optional<String> result = libraryAnalyticsAnswerService.tryAnswer("/查询 x");

        // Then
        assertTrue(result.isPresent());
        assertTrue(result.get().contains("统计分析链路未返回内容"));
    }

    @Test
    @DisplayName("强制查询助手抛异常 - 返回执行失败说明")
    void testTryAnswer_Forced_AssistantThrows_ReturnsErrorHint() {
        // Given
        when(libraryAnalyticsAssistant.answer("x")).thenThrow(new RuntimeException("boom"));

        // When
        Optional<String> result = libraryAnalyticsAnswerService.tryAnswer("/query x");

        // Then
        assertTrue(result.isPresent());
        assertTrue(result.get().contains("统计分析链路执行失败"));
    }

    @Test
    @DisplayName("非强制统计问题助手抛异常 - 静默 empty")
    void testTryAnswer_NotForced_AssistantThrows_ReturnsEmpty() {
        // Given
        when(structuredAnalyticsIntentDetector.isAnalyticsQuestion("共有多少种书")).thenReturn(true);
        when(libraryAnalyticsAssistant.answer("共有多少种书")).thenThrow(new RuntimeException("down"));

        // When
        Optional<String> result = libraryAnalyticsAnswerService.tryAnswer("共有多少种书");

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("自我借阅分析问题 - 拼接 user_id=1 规则提示")
    void testTryAnswer_SelfBorrowPrependsRulePrompt() {
        // Given
        when(structuredAnalyticsIntentDetector.isAnalyticsQuestion("我借阅过哪些书")).thenReturn(true);
        when(libraryAnalyticsAssistant.answer(argThat(s -> s.contains("user_id = 1") && s.contains("我借阅过哪些书"))))
                .thenReturn("ok");

        // When
        Optional<String> result = libraryAnalyticsAnswerService.tryAnswer("我借阅过哪些书");

        // Then
        assertTrue(result.isPresent());
        assertEquals("ok", result.get());
    }
}
