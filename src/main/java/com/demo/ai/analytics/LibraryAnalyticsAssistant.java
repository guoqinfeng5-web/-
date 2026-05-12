package com.demo.ai.analytics;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 馆藏统计分析对话接口，由 LangChain4j {@code AiServices} 生成实现；绑定 Tool 后自动完成「对话 → 调工具 → 再生成」循环。
 */
public interface LibraryAnalyticsAssistant {

    @SystemMessage(LibraryAnalyticsSchema.SYSTEM_PROMPT)
    String answer(@UserMessage String userQuestion);
}
