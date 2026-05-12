package com.demo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * DashScope Embedding 配置（供 VectorStoreService 使用）。
 */
@Data
@ConfigurationProperties(prefix = "dashscope.embedding")
public class DashScopeEmbeddingProperties {

    /**
     * DashScope API Key。
     */
    private String apiKey;

    /**
     * Embedding 模型名，例如 text-embedding-v2 / text-embedding-v4。
     */
    private String modelName = "text-embedding-v2";

    /**
     * DashScope OpenAI 兼容模式 baseUrl，例如：
     * https://dashscope.aliyuncs.com/compatible-mode/v1
     */
    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
}

