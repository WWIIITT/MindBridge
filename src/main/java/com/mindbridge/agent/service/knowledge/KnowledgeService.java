package com.mindbridge.agent.service.knowledge;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindbridge.agent.config.MindBridgeProperties;
import com.mindbridge.agent.domain.KnowledgeChunk;
import com.mindbridge.agent.repository.KnowledgeChunkRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
/**
 * RAG 知識庫核心服務。
 *
 * <p>負責知識入庫、向量寫入、檢索排序和命中上下文擴展，是諮詢/風險回答的知識來源。</p>
 */
public class KnowledgeService {

    private final KnowledgeChunkRepository knowledgeChunkRepository;
    private final MindBridgeProperties properties;
    private final ChromaGateway chromaGateway;
    private final EmbeddingClient embeddingClient;
    private final ObjectMapper objectMapper;
    private final KnowledgeChunker chunker = new KnowledgeChunker();
    private final TokenVectorizer vectorizer = new TokenVectorizer();

    public KnowledgeService(
            KnowledgeChunkRepository knowledgeChunkRepository,
            MindBridgeProperties properties,
            ChromaGateway chromaGateway,
            EmbeddingClient embeddingClient,
            ObjectMapper objectMapper
    ) {
        this.knowledgeChunkRepository = knowledgeChunkRepository;
        this.properties = properties;
        this.chromaGateway = chromaGateway;
        this.embeddingClient = embeddingClient;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public int ingest(String source, String content) {
        // 同一 source 重新上傳時先清舊數據，保證後臺知識庫展示的是最新文件內容。
        List<String> chunks = chunker.chunk(
                content,
                properties.getKnowledge().getChunkSize(),
                properties.getKnowledge().getChunkOverlap());
        knowledgeChunkRepository.deleteBySource(source);
        chromaGateway.deleteSource(source);
        for (int index = 0; index < chunks.size(); index++) {
            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setSource(source);
            chunk.setSourceIndex(index);
            chunk.setContent(chunks.get(index));
            // 有 embedding 配置時寫入向量；沒有配置時保持爲空，檢索會自動走本地兜底。
            chunk.setEmbeddingJson(serializeEmbedding(safeEmbedding(chunks.get(index))));
            KnowledgeChunk saved = knowledgeChunkRepository.save(chunk);
            chromaGateway.mirror(saved);
        }
        return chunks.size();
    }

    @Transactional(readOnly = true)
    public List<SearchResult> retrieve(String query, int topK) {
        // 檢索優先級：外部向量庫 -> 數據庫存儲的 embedding -> 本地輕量打分。
        List<SearchResult> chromaResults = chromaGateway.query(query, topK);
        if (!chromaResults.isEmpty()) {
            return expandBestContext(chromaResults, topK);
        }
        List<SearchResult> embeddingResults = retrieveByEmbedding(query, topK);
        if (!embeddingResults.isEmpty()) {
            return expandBestContext(embeddingResults, topK);
        }
        List<SearchResult> ranked = knowledgeChunkRepository.findAll().stream()
                .map(chunk -> new SearchResult(
                        chunk.getId(),
                        chunk.getSource(),
                        chunk.getContent(),
                        hybridScore(query, chunk.getContent())))
                .filter(result -> result.score() > 0.0)
                .sorted(Comparator.comparingDouble(SearchResult::score).reversed())
                .limit(topK)
                .toList();
        return expandBestContext(ranked, topK);
    }

    private List<SearchResult> retrieveByEmbedding(String query, int topK) {
        List<Double> queryEmbedding = safeEmbedding(query);
        if (queryEmbedding.isEmpty()) {
            return List.of();
        }
        return knowledgeChunkRepository.findAll().stream()
                .map(chunk -> new SearchResult(
                        chunk.getId(),
                        chunk.getSource(),
                        chunk.getContent(),
                        cosine(queryEmbedding, parseEmbedding(chunk.getEmbeddingJson()))))
                .filter(result -> result.score() > 0.0)
                .sorted(Comparator.comparingDouble(SearchResult::score).reversed())
                .limit(topK)
                .toList();
    }

    private List<SearchResult> expandBestContext(List<SearchResult> ranked, int topK) {
        if (ranked.isEmpty()) {
            return ranked;
        }
        // 命中片段前後各補一段，減少切塊邊界導致的上下文斷裂。
        SearchResult best = ranked.get(0);
        SearchResult expanded = expand(best);
        List<SearchResult> results = new ArrayList<>();
        results.add(expanded);
        ranked.stream()
                .skip(1)
                .filter(result -> !sameChunk(result, expanded))
                .limit(Math.max(0, topK - 1))
                .forEach(results::add);
        return results;
    }

    private SearchResult expand(SearchResult result) {
        if (result.chunkId() == null) {
            return result;
        }
        return knowledgeChunkRepository.findById(result.chunkId())
                .map(chunk -> {
                    List<KnowledgeChunk> neighbors = knowledgeChunkRepository
                            .findBySourceAndSourceIndexBetweenOrderBySourceIndexAsc(
                                    chunk.getSource(),
                                    Math.max(0, chunk.getSourceIndex() - 1),
                                    chunk.getSourceIndex() + 1);
                    String expandedContent = String.join("\n\n", neighbors.stream()
                            .map(KnowledgeChunk::getContent)
                            .toList());
                    return new SearchResult(chunk.getId(), chunk.getSource(), expandedContent, result.score());
                })
                .orElse(result);
    }

    private boolean sameChunk(SearchResult result, SearchResult expanded) {
        return result.chunkId() != null && result.chunkId().equals(expanded.chunkId());
    }

    private double hybridScore(String query, String content) {
        double semantic = vectorizer.cosine(query, content);
        double keyword = keywordScore(query, content);
        return semantic * 0.75 + keyword * 0.25;
    }

    private List<Double> safeEmbedding(String text) {
        try {
            return embeddingClient.embed(text);
        } catch (Exception ignored) {
            // embedding 失敗不影響知識庫可用性，後續會回退到本地檢索。
            return List.of();
        }
    }

    private String serializeEmbedding(List<Double> embedding) {
        if (embedding == null || embedding.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(embedding);
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<Double> parseEmbedding(String embeddingJson) {
        if (embeddingJson == null || embeddingJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(embeddingJson, new TypeReference<>() {
            });
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private double cosine(List<Double> left, List<Double> right) {
        if (left.isEmpty() || right.isEmpty() || left.size() != right.size()) {
            return 0.0;
        }
        double dot = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;
        for (int i = 0; i < left.size(); i++) {
            double a = left.get(i);
            double b = right.get(i);
            dot += a * b;
            leftNorm += a * a;
            rightNorm += b * b;
        }
        if (leftNorm == 0.0 || rightNorm == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private double keywordScore(String query, String content) {
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        String normalizedContent = content.toLowerCase(Locale.ROOT);
        List<String> terms = List.of(normalizedQuery.split("[\\s，。！？、；：,.!?;:]+"));
        long matched = terms.stream()
                .filter(term -> term.length() >= 2)
                .filter(normalizedContent::contains)
                .count();
        return terms.isEmpty() ? 0.0 : Math.min(1.0, matched / (double) terms.size());
    }
}
