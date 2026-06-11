package com.mindbridge.agent.service.agent;

import com.mindbridge.agent.config.MindBridgeProperties;
import com.mindbridge.agent.domain.IntentType;
import com.mindbridge.agent.service.ai.AiClient;
import com.mindbridge.agent.service.ai.AiMessage;
import com.mindbridge.agent.service.knowledge.KnowledgeService;
import com.mindbridge.agent.service.knowledge.SearchResult;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 知識庫 Agent。
 *
 * <p>只有心理諮詢和風險場景才檢索 Chroma/RAG，普通學習閒聊不會被強行轉成心理測評。</p>
 */
@Component
public class KnowledgeAgent implements MindBridgeAgent {

    private final KnowledgeService knowledgeService;
    private final MindBridgeProperties properties;
    private final AiClient aiClient;

    public KnowledgeAgent(KnowledgeService knowledgeService, MindBridgeProperties properties, AiClient aiClient) {
        this.knowledgeService = knowledgeService;
        this.properties = properties;
        this.aiClient = aiClient;
    }

    @Override
    public AgentName name() {
        return AgentName.KNOWLEDGE_AGENT;
    }

    @Override
    public boolean supports(AgentContext context) {
        return context.intentRouted()
                && !context.knowledgeHandled()
                && context.intent() != IntentType.CHAT;
    }

    @Override
    public AgentDecision act(AgentContext context) {
        String query = rewriteQuery(context);
        List<SearchResult> retrieved = knowledgeService.retrieve(query, properties.getKnowledge().getTopK());
        String observation = "query=%s; retrieved=%d".formatted(query, retrieved.size());
        if (!isKnowledgeEnough(context, retrieved)) {
            String refinedQuery = refineQuery(context, query, retrieved);
            if (!refinedQuery.equals(query)) {
                List<SearchResult> refined = knowledgeService.retrieve(refinedQuery, properties.getKnowledge().getTopK());
                if (!refined.isEmpty()) {
                    query = refinedQuery;
                    retrieved = refined;
                    observation = "query=%s; refined=true; retrieved=%d".formatted(query, retrieved.size());
                }
            }
        }
        context.setKnowledgeQuery(query);
        context.setRetrievedKnowledge(retrieved);
        context.markKnowledgeHandled();
        return AgentDecision.continueWith(
                AgentAction.RETRIEVE_KNOWLEDGE,
                observation);
    }

    private String rewriteQuery(AgentContext context) {
        try {
            String query = aiClient.complete(List.of(
                    AiMessage.system("""
                            你是 MindBridge 的 KnowledgeAgent。
                            你的任務是把學生輸入改寫成適合檢索校園心理知識庫的中文查詢詞。
                            只輸出查詢詞本身，不要解釋，不要超過 40 個字。
                            聚焦心理支持、校園求助流程、風險處理或情緒調節知識。
                            """),
                    AiMessage.user("""
                            記憶摘要：
                            %s

                            當前輸入：
                            %s
                            """.formatted(context.memoryBrief(), context.modelInput()))
            )).trim();
            return normalizeQuery(query, context.modelInput());
        } catch (Exception ignored) {
            return context.modelInput();
        }
    }

    private boolean isKnowledgeEnough(AgentContext context, List<SearchResult> results) {
        if (results.isEmpty()) {
            return false;
        }
        try {
            String decision = aiClient.complete(List.of(
                    AiMessage.system("""
                            你是 MindBridge 的 KnowledgeAgent。
                            判斷檢索結果是否足以支持後續心理關懷回答。
                            只輸出 SUFFICIENT 或 INSUFFICIENT。
                            """),
                    AiMessage.user("""
                            當前輸入：
                            %s

                            檢索結果：
                            %s
                            """.formatted(context.modelInput(), formatResults(results)))
            )).trim().toUpperCase();
            return decision.contains("SUFFICIENT") && !decision.contains("INSUFFICIENT");
        } catch (Exception ignored) {
            return true;
        }
    }

    private String refineQuery(AgentContext context, String previousQuery, List<SearchResult> results) {
        try {
            String query = aiClient.complete(List.of(
                    AiMessage.system("""
                            你是 MindBridge 的 KnowledgeAgent。
                            上一次檢索信息不足，請給出一個新的、更具體的中文檢索 query。
                            只輸出查詢詞本身，不要解釋，不要超過 40 個字。
                            """),
                    AiMessage.user("""
                            當前輸入：
                            %s

                            上一次 query：
                            %s

                            上一次結果：
                            %s
                            """.formatted(context.modelInput(), previousQuery, formatResults(results)))
            )).trim();
            return normalizeQuery(query, previousQuery);
        } catch (Exception ignored) {
            return previousQuery;
        }
    }

    private String formatResults(List<SearchResult> results) {
        if (results.isEmpty()) {
            return "無";
        }
        return String.join("\n", results.stream()
                .limit(4)
                .map(result -> "- " + result.content())
                .toList());
    }

    private String normalizeQuery(String value, String fallback) {
        String query = value
                .replace("查詢詞：", "")
                .replace("query:", "")
                .replace("Query:", "")
                .replaceAll("[\\r\\n]+", " ")
                .trim();
        if (query.isBlank()) {
            return fallback;
        }
        return query.length() > 60 ? query.substring(0, 60) : query;
    }
}
