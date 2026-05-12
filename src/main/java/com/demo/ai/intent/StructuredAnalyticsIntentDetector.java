package com.demo.ai.intent;

import com.demo.common.AppConstants;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 判断用户问题是否更适合走「统计 / 聚合 / 排行」类 NL2SQL，而非纯推荐或闲聊。
 * 正则定义见 {@link AppConstants#ANALYTICS_INTENT_PATTERN}。
 */
@Component
public class StructuredAnalyticsIntentDetector {

    public boolean isAnalyticsQuestion(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        return AppConstants.ANALYTICS_INTENT_PATTERN.matcher(question.toLowerCase(Locale.ROOT)).find();
    }
}
