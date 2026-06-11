package com.mindbridge.agent.service.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.mindbridge.agent.config.MindBridgeProperties;
import com.mindbridge.agent.domain.KnowledgeChunk;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
/**
 * Chroma 向量庫網關。
 *
 * <p>當 use-chroma=true 時，把知識庫切塊鏡像到外部向量庫，並優先從 Chroma 檢索。</p>
 */
public class ChromaGateway {

    private final MindBridgeProperties properties;
    private final WebClient webClient;
    private volatile boolean collectionEnsured;

    public ChromaGateway(MindBridgeProperties properties, WebClient.Builder webClientBuilder) {
        this.properties = properties;
        this.webClient = webClientBuilder.baseUrl(properties.getKnowledge().getChromaBaseUrl()).build();
    }

    public void mirror(KnowledgeChunk chunk) {
        if (!properties.getKnowledge().isUseChroma()) {
            return;
        }
        // 本地數據庫仍是主存儲；Chroma 只是可選檢索加速層。
        ensureCollection();
        Map<String, Object> body = Map.of(
                "ids", List.of(String.valueOf(chunk.getId())),
                "documents", List.of(chunk.getContent()),
                "metadatas", List.of(Map.of(
                        "source", chunk.getSource(),
                        "sourceIndex", chunk.getSourceIndex()))
        );
        webClient.post()
                .uri("/api/v1/collections/{collection}/add", properties.getKnowledge().getChromaCollection())
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .onErrorComplete()
                .block();
    }

    public List<SearchResult> query(String text, int topK) {
        if (!properties.getKnowledge().isUseChroma()) {
            return List.of();
        }
        ensureCollection();
        Map<String, Object> body = Map.of(
                "query_texts", List.of(text),
                "n_results", topK,
                "include", List.of("documents", "metadatas", "distances")
        );
        try {
            JsonNode response = webClient.post()
                    .uri("/api/v1/collections/{collection}/query", properties.getKnowledge().getChromaCollection())
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            return parseResults(response);
        } catch (Exception ignored) {
            // 外部向量庫不可用時返回空結果，讓 KnowledgeService 回退到本地檢索。
            return List.of();
        }
    }

    public void deleteSource(String source) {
        if (!properties.getKnowledge().isUseChroma()) {
            return;
        }
        ensureCollection();
        Map<String, Object> body = Map.of("where", Map.of("source", source));
        webClient.post()
                .uri("/api/v1/collections/{collection}/delete", properties.getKnowledge().getChromaCollection())
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .onErrorComplete()
                .block();
    }

    private List<SearchResult> parseResults(JsonNode response) {
        if (response == null) {
            return List.of();
        }
        List<SearchResult> results = new ArrayList<>();
        JsonNode docs = response.path("documents").path(0);
        JsonNode ids = response.path("ids").path(0);
        JsonNode metadatas = response.path("metadatas").path(0);
        JsonNode distances = response.path("distances").path(0);
        for (int i = 0; i < docs.size(); i++) {
            Long id = parseId(ids.path(i).asText());
            double score = 1.0 - distances.path(i).asDouble(1.0);
            String source = metadatas.path(i).path("source").asText("chroma");
            results.add(new SearchResult(id, source, docs.path(i).asText(), score));
        }
        return results;
    }

    private Long parseId(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void ensureCollection() {
        if (collectionEnsured) {
            return;
        }
        try {
            webClient.post()
                    .uri("/api/v1/collections")
                    .bodyValue(Map.of("name", properties.getKnowledge().getChromaCollection()))
                    .retrieve()
                    .toBodilessEntity()
                    .onErrorComplete()
                    .block();
        } finally {
            collectionEnsured = true;
        }
    }
}
