package com.mindbridge.agent.config;

import com.mindbridge.agent.service.ai.AiClient;
import com.mindbridge.agent.service.ai.HeuristicAiClient;
import com.mindbridge.agent.service.ai.SpringAiChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI 大模型客戶端裝配配置。
 *
 * <p>根據 application.yml 或環境變量選擇本地項目模型、OpenAI 或 mock 客戶端，
 * 讓業務服務只依賴統一的 {@link AiClient} 接口。</p>
 */
@Configuration
public class AiClientConfig {

    @Bean
    public AiClient aiClient(MindBridgeProperties properties) {
        String provider = properties.getAi().getProvider().toLowerCase();
        if ("ollama".equals(provider)) {
            OllamaChatModel model = ollamaChatModel(properties);
            return new SpringAiChatClient(model, model);
        }
        if ("openai".equals(provider)) {
            if (properties.getAi().getOpenai().getApiKey().isBlank()) {
                throw new IllegalStateException("AI_PROVIDER=openai requires OPENAI_API_KEY.");
            }
            OpenAiChatModel model = openAiChatModel(properties);
            return new SpringAiChatClient(model, model);
        }
        return new HeuristicAiClient();
    }

    private OllamaChatModel ollamaChatModel(MindBridgeProperties properties) {
        MindBridgeProperties.Ollama ollama = properties.getAi().getOllama();
        OllamaApi api = OllamaApi.builder()
                .baseUrl(ollama.getBaseUrl())
                .build();
        OllamaOptions options = OllamaOptions.builder()
                .model(ollama.getModel())
                .temperature(properties.getAi().getTemperature())
                .numPredict(properties.getAi().getMaxTokens())
                .topP(0.85)
                .repeatPenalty(1.12)
                .build();
        return OllamaChatModel.builder()
                .ollamaApi(api)
                .defaultOptions(options)
                .build();
    }

    private OpenAiChatModel openAiChatModel(MindBridgeProperties properties) {
        MindBridgeProperties.OpenAi openai = properties.getAi().getOpenai();
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(openai.getBaseUrl())
                .apiKey(openai.getApiKey())
                .build();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(openai.getModel())
                .temperature(properties.getAi().getTemperature())
                .maxTokens(properties.getAi().getMaxTokens())
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options)
                .build();
    }
}
