package com.mindbridge.agent.controller;

import com.mindbridge.agent.config.MindBridgeProperties;
import java.util.Locale;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
/**
 * 智能體運行狀態接口。
 *
 * <p>前端用它展示當前 provider、項目模型名稱、RAG 參數和模型連接模式。</p>
 */
public class AgentStatusController {

    private final MindBridgeProperties properties;

    public AgentStatusController(MindBridgeProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/status")
    public AgentStatusResponse status() {
        // realModelEnabled 只表示當前使用真實模型客戶端，不代表業務評估一定會展示給學生。
        String provider = properties.getAi().getProvider().toLowerCase(Locale.ROOT);
        boolean realModelEnabled = "ollama".equals(provider) || "openai".equals(provider);
        return new AgentStatusResponse(
                provider,
                modelName(provider),
                realModelEnabled,
                properties.getKnowledge().isUseChroma(),
                properties.getKnowledge().getTopK(),
                realModelEnabled ? "正在使用真實大模型客戶端。" : "當前爲本地 mock 演示模式，不會調用大模型。"
        );
    }

    private String modelName(String provider) {
        if ("ollama".equals(provider)) {
            return properties.getAi().getOllama().getModel();
        }
        if ("openai".equals(provider)) {
            return properties.getAi().getOpenai().getModel();
        }
        return "heuristic-local";
    }

    /**
     * 前端狀態欄需要的最小狀態信息。
     */
    public record AgentStatusResponse(
            String provider,
            String model,
            boolean realModelEnabled,
            boolean chromaEnabled,
            int ragTopK,
            String note
    ) {
    }
}
