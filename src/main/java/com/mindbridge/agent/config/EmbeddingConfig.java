package com.mindbridge.agent.config;

import com.mindbridge.agent.service.knowledge.EmbeddingClient;
import com.mindbridge.agent.service.knowledge.OpenAiEmbeddingClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
/**
 * RAG 向量化客戶端配置。
 *
 * <p>KnowledgeService 通過 EmbeddingClient 獲取文本向量；如果沒有配置 API Key，
 * 客戶端會返回空向量並觸發本地檢索兜底。</p>
 */
public class EmbeddingConfig {

    @Bean
    public EmbeddingClient embeddingClient(
            MindBridgeProperties properties,
            WebClient.Builder webClientBuilder
    ) {
        return new OpenAiEmbeddingClient(properties, webClientBuilder);
    }
}
