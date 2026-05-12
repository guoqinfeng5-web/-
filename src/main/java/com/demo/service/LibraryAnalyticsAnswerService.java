package com.demo.service;

import com.demo.ai.analytics.LibraryAnalyticsAssistant;
import com.demo.ai.intent.StructuredAnalyticsIntentDetector;
import com.demo.common.AppConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 编排「意图 → 带 Tool 的统计分析对话」；与 {@link AiService} 的 RAG 路径解耦。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LibraryAnalyticsAnswerService {

    private static final String SELF_BORROW_RULE_PROMPT = """
            【当前会话默认用户规则】
            若本轮问题是在问“我 / 我的 / 本人 / 自己”的借阅分析，且需要查询 borrow_records，
            则必须将当前用户固定视为 user_id = 1，并在 SQL 中显式加上 borrow_records.user_id = 1
            （或等价的 user_id = 1 条件），不要查询其他用户，也不要省略该条件。

            用户问题：
            """;

    private final LibraryAnalyticsAssistant libraryAnalyticsAssistant;
    private final StructuredAnalyticsIntentDetector structuredAnalyticsIntentDetector;

    /**
     * 若问题属于统计分析意图，则走 NL2SQL（Tool）链路并返回答案；否则返回 empty。
     */
    public Optional<String> tryAnswer(String question) {
        if (question == null || question.isBlank()) {
            return Optional.empty();
        }

        String trimmed = question.trim();
        boolean forced = isForcedAnalytics(trimmed);
        String actualQuestion = forced ? stripForcedPrefix(trimmed) : trimmed;

        if (actualQuestion.isBlank()) {
            return Optional.empty();
        }

        if (!forced && !structuredAnalyticsIntentDetector.isAnalyticsQuestion(actualQuestion)) {
            return Optional.empty();
        }
        try {
            String promptQuestion = isSelfBorrowAnalyticsQuestion(actualQuestion)
                    ? SELF_BORROW_RULE_PROMPT + actualQuestion
                    : actualQuestion;
            String raw = libraryAnalyticsAssistant.answer(promptQuestion);
            if (raw == null || raw.isBlank()) {
                // forced 模式下，空输出也应显式报错，避免误导用户为“系统回退/隐私拒绝”
                if (forced) {
                    return Optional.of("统计分析链路未返回内容：请检查服务日志（模型调用是否成功、Tool 是否绑定并触发 queryLibraryDatabase、数据库连接是否正常）。");
                }
                return Optional.empty();
            }
            return Optional.of(raw.trim());
        } catch (Exception e) {
            // forced 模式下不要静默回退，否则会让用户以为系统“拒绝隐私”而不是链路故障
            if (forced) {
                log.warn("统计分析助手调用失败（forced），请检查模型/Tool/数据库链路", e);
                return Optional.of("统计分析链路执行失败：请检查服务日志（模型调用 / Tool 绑定 / 数据库连接 / SQL 校验）。");
            }
            log.warn("统计分析助手调用失败，将回退 RAG: {}", e.toString(), e);
            return Optional.empty();
        }
    }

    private static boolean isForcedAnalytics(String question) {
        return question.startsWith(AppConstants.FORCE_ANALYTICS_PREFIX_CN)
                || question.toLowerCase().startsWith(AppConstants.FORCE_ANALYTICS_PREFIX_EN);
    }

    private static String stripForcedPrefix(String question) {
        if (question.startsWith(AppConstants.FORCE_ANALYTICS_PREFIX_CN)) {
            return question.substring(AppConstants.FORCE_ANALYTICS_PREFIX_CN.length()).trim();
        }
        String lower = question.toLowerCase();
        if (lower.startsWith(AppConstants.FORCE_ANALYTICS_PREFIX_EN)) {
            return question.substring(AppConstants.FORCE_ANALYTICS_PREFIX_EN.length()).trim();
        }
        return question.trim();
    }

    private static boolean isSelfBorrowAnalyticsQuestion(String question) {
        return containsSelfReference(question) && containsBorrowIntent(question);
    }

    private static boolean containsSelfReference(String question) {
        return question.contains("我")
                || question.contains("我的")
                || question.contains("我自己")
                || question.contains("本人")
                || question.contains("自己");
    }

    private static boolean containsBorrowIntent(String question) {
        return question.contains("借阅")
                || question.contains("借书")
                || question.contains("借过")
                || question.contains("在借")
                || question.contains("未归还")
                || question.contains("归还");
    }
}
