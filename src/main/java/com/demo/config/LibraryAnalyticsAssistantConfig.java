package com.demo.config;

import com.demo.ai.analytics.LibraryAnalyticsAssistant;
import com.demo.ai.tools.LibraryAnalyticsTools;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 使用 LangChain4j {@link AiServices} 绑定 {@link ChatModel} 与统计分析 Tool。
 */
@Configuration
public class LibraryAnalyticsAssistantConfig {

    @Bean
    public LibraryAnalyticsAssistant libraryAnalyticsAssistant(
            ChatModel chatModel,
            LibraryAnalyticsTools libraryAnalyticsTools) {
        return AiServices.builder(LibraryAnalyticsAssistant.class)
                .chatModel(chatModel)
                .tools(libraryAnalyticsTools)
                .build();
    }
}
