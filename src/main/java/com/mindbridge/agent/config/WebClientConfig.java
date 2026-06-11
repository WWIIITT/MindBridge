package com.mindbridge.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
/**
 * 共享 WebClient.Builder。
 *
 * <p>模型服務、embedding 服務和 HTTP MCP 工具都複用這個 Builder，便於統一擴展超時、
 * 日誌或代理設置。</p>
 */
public class WebClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}
