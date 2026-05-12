package com.demo.ai.intent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("统计分析意图检测单元测试")
class StructuredAnalyticsIntentDetectorTest {

    private final StructuredAnalyticsIntentDetector detector = new StructuredAnalyticsIntentDetector();

    @Test
    @DisplayName("统计类问题 - 命中分析意图")
    void testIsAnalyticsQuestion_StatsKeywords_ReturnsTrue() {
        // Given
        String q = "按分类统计馆藏有多少种书";

        // When
        boolean analytics = detector.isAnalyticsQuestion(q);

        // Then
        assertTrue(analytics);
    }

    @Test
    @DisplayName("占比/排行类问题 - 命中分析意图")
    void testIsAnalyticsQuestion_RatioOrRank_ReturnsTrue() {
        // Given
        String ratio = "计算机类图书占比是多少";
        String rank = "借阅次数排行前10的书";

        // When & Then
        assertTrue(detector.isAnalyticsQuestion(ratio));
        assertTrue(detector.isAnalyticsQuestion(rank));
    }

    @Test
    @DisplayName("英文聚合关键词 - 命中分析意图")
    void testIsAnalyticsQuestion_EnglishKeywords_ReturnsTrue() {
        // Given
        String q = "how many books are in the library";

        // When
        boolean analytics = detector.isAnalyticsQuestion(q);

        // Then
        assertTrue(analytics);
    }

    @Test
    @DisplayName("推荐/闲聊类问题 - 不命中分析意图")
    void testIsAnalyticsQuestion_ChitchatOrRecommend_ReturnsFalse() {
        // Given
        String recommend = "推荐几本适合初学者的Java书";
        String hello = "你好，最近怎么样";

        // When & Then
        assertFalse(detector.isAnalyticsQuestion(recommend));
        assertFalse(detector.isAnalyticsQuestion(hello));
    }

    @Test
    @DisplayName("空或空白问题 - 不命中")
    void testIsAnalyticsQuestion_Blank_ReturnsFalse() {
        // When & Then
        assertFalse(detector.isAnalyticsQuestion(null));
        assertFalse(detector.isAnalyticsQuestion(""));
        assertFalse(detector.isAnalyticsQuestion("   "));
    }
}
