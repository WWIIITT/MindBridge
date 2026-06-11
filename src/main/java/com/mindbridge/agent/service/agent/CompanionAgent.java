package com.mindbridge.agent.service.agent;

import com.mindbridge.agent.domain.IntentType;
import com.mindbridge.agent.domain.RiskLevel;
import com.mindbridge.agent.service.ai.AiClient;
import com.mindbridge.agent.service.ai.AiMessage;
import com.mindbridge.agent.service.ai.PromptTemplates;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 普通陪伴 Agent。
 *
 * <p>處理學習、生活、編程、校園事務等普通聊天，不生成後臺心理報告。</p>
 */
@Component
public class CompanionAgent implements MindBridgeAgent {

    private final AiClient aiClient;

    public CompanionAgent(AiClient aiClient) {
        this.aiClient = aiClient;
    }

    @Override
    public AgentName name() {
        return AgentName.COMPANION_AGENT;
    }

    @Override
    public boolean supports(AgentContext context) {
        return context.intentRouted()
                && context.intent() == IntentType.CHAT
                && !context.responsePlanned();
    }

    @Override
    public AgentDecision act(AgentContext context) {
        context.setRiskLevel(RiskLevel.LOW);
        context.setResponseAgent(AgentName.COMPANION_AGENT);
        String plan = planResponse(context);
        context.setResponsePlan(plan);
        context.setResponseMessages(buildResponseMessages(context, plan));
        context.markResponsePlanned();
        return AgentDecision.finish(AgentAction.PLAN_RESPONSE, "normal companion response planned by model");
    }

    private String planResponse(AgentContext context) {
        try {
            String plan = aiClient.complete(List.of(
                    AiMessage.system("""
                            你是 MindBridge 的 CompanionAgent。
                            你負責普通學習、生活、校園事務、編程和日常聊天。
                            請根據當前輸入和記憶摘要，制定一句簡短回覆策略。
                            不要做心理評估，不要輸出風險等級，不要替用戶下診斷。
                            """),
                    AiMessage.user("""
                            記憶摘要：
                            %s

                            當前輸入：
                            %s
                            """.formatted(context.memoryBrief(), context.modelInput()))
            )).trim();
            return plan.isBlank() ? "圍繞用戶當前問題直接、自然地回答。" : shorten(plan, 300);
        } catch (Exception ignored) {
            return "圍繞用戶當前問題直接、自然地回答。";
        }
    }

    private List<AiMessage> buildResponseMessages(AgentContext context, String plan) {
        List<AiMessage> messages = new ArrayList<>();
        messages.add(PromptTemplates.answerSystemPrompt(
                IntentType.CHAT,
                RiskLevel.LOW,
                "",
                context.user().getDisplayName()));
        messages.add(AiMessage.system("""
                當前由 CompanionAgent 負責回覆。
                記憶摘要：
                %s

                回覆策略：
                %s
                """.formatted(context.memoryBrief(), plan)));
        messages.addAll(context.modelHistory());
        return messages;
    }

    private String shorten(String value, int maxLength) {
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
