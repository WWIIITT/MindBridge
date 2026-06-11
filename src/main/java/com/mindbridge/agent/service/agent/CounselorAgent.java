package com.mindbridge.agent.service.agent;

import com.mindbridge.agent.domain.IntentType;
import com.mindbridge.agent.service.ai.AiClient;
import com.mindbridge.agent.service.ai.AiMessage;
import com.mindbridge.agent.service.ai.PromptTemplates;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 心理支持 Agent。
 *
 * <p>處理諮詢和風險場景，回覆會結合 RAG 知識與 RiskGuardian 的後臺評估結果。</p>
 */
@Component
public class CounselorAgent implements MindBridgeAgent {

    private final AiClient aiClient;

    public CounselorAgent(AiClient aiClient) {
        this.aiClient = aiClient;
    }

    @Override
    public AgentName name() {
        return AgentName.COUNSELOR_AGENT;
    }

    @Override
    public boolean supports(AgentContext context) {
        return context.riskAssessed()
                && context.intent() != IntentType.CHAT
                && !context.responsePlanned();
    }

    @Override
    public AgentDecision act(AgentContext context) {
        context.setResponseAgent(AgentName.COUNSELOR_AGENT);
        String plan = planResponse(context);
        context.setResponsePlan(plan);
        context.setResponseMessages(buildResponseMessages(context, plan));
        context.markResponsePlanned();
        return AgentDecision.finish(
                AgentAction.PLAN_RESPONSE,
                "support response planned by model with risk=%s".formatted(context.riskLevel()));
    }

    private String planResponse(AgentContext context) {
        try {
            String plan = aiClient.complete(List.of(
                    AiMessage.system("""
                            你是 MindBridge 的 CounselorAgent。
                            你負責心理支持式回應策略，不直接給診斷。
                            請結合記憶摘要、風險守護結果和知識庫命中，制定 2-3 句回覆策略。
                            高風險時必須優先保護學生安全。
                            不要輸出後臺標籤、風險等級、分數或報告口吻。
                            """),
                    AiMessage.user("""
                            記憶摘要：
                            %s

                            當前輸入：
                            %s

                            風險守護結果：
                            %s

                            知識庫 query：
                            %s

                            知識庫命中：
                            %s
                            """.formatted(
                            context.memoryBrief(),
                            context.modelInput(),
                            context.assessment() == null ? "無" : context.assessment().summary(),
                            context.knowledgeQuery(),
                            formatKnowledge(context)))
            )).trim();
            return plan.isBlank() ? "先共情，再給出具體支持步驟；高風險時優先安全。" : shorten(plan, 500);
        } catch (Exception ignored) {
            return "先共情，再給出具體支持步驟；高風險時優先安全。";
        }
    }

    private List<AiMessage> buildResponseMessages(AgentContext context, String plan) {
        String knowledgeContext = String.join("\n\n", context.retrievedKnowledge().stream()
                .map(result -> "- [" + result.source() + "] " + result.content())
                .toList());
        List<AiMessage> messages = new ArrayList<>();
        messages.add(PromptTemplates.answerSystemPrompt(
                context.intent(),
                context.riskLevel(),
                knowledgeContext,
                context.user().getDisplayName()));
        messages.add(AiMessage.system("""
                當前由 CounselorAgent 負責回覆。
                記憶摘要：
                %s

                KnowledgeAgent 檢索 query：
                %s

                回覆策略：
                %s
                """.formatted(context.memoryBrief(), context.knowledgeQuery(), plan)));
        messages.addAll(context.modelHistory());
        return messages;
    }

    private String formatKnowledge(AgentContext context) {
        if (context.retrievedKnowledge().isEmpty()) {
            return "無";
        }
        return String.join("\n", context.retrievedKnowledge().stream()
                .limit(4)
                .map(result -> "- " + result.content())
                .toList());
    }

    private String shorten(String value, int maxLength) {
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
