package com.mindbridge.agent.service.knowledge.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mindbridge.agent.service.knowledge.KnowledgeService;
import com.mindbridge.agent.service.knowledge.SearchResult;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class RagEvaluationService {

    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.0000");

    private final KnowledgeService knowledgeService;
    private final ObjectMapper objectMapper;
    private final DefaultResourceLoader resourceLoader = new DefaultResourceLoader();

    public RagEvaluationService(KnowledgeService knowledgeService, ObjectMapper objectMapper) {
        this.knowledgeService = knowledgeService;
        this.objectMapper = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public RagEvalReport evaluate(String datasetLocation, int topK) {
        List<RagEvalCase> cases = loadDataset(datasetLocation);
        List<RagEvalCaseResult> results = cases.stream()
                .map(testCase -> evaluateCase(testCase, topK))
                .toList();
        double total = Math.max(1, results.size());
        List<RagEvalCaseResult> hits = results.stream()
                .filter(RagEvalCaseResult::hit)
                .toList();
        double averageFirstRelevantRank = hits.isEmpty()
                ? 0.0
                : hits.stream().mapToInt(RagEvalCaseResult::firstRelevantRank).average().orElse(0.0);
        return new RagEvalReport(
                Instant.now(),
                datasetLocation,
                topK,
                results.size(),
                average(results, RagEvalCaseResult::recallAtK, total),
                average(results, RagEvalCaseResult::precisionAtK, total),
                average(results, RagEvalCaseResult::reciprocalRank, total),
                average(results, RagEvalCaseResult::ndcgAtK, total),
                hits.size() / total,
                averageFirstRelevantRank,
                results);
    }

    public void writeReport(RagEvalReport report, String outputPath) {
        if (outputPath == null || outputPath.isBlank()) {
            return;
        }
        try {
            Path path = Path.of(outputPath);
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            objectMapper.writeValue(path.toFile(), report);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to write RAG evaluation report: " + outputPath, exception);
        }
    }

    public String formatSummary(RagEvalReport report) {
        return """
                RAG evaluation completed.
                dataset=%s
                cases=%d
                topK=%d
                recall@K=%s
                precision@K=%s
                mrr=%s
                ndcg@K=%s
                hitRate=%s
                averageFirstRelevantRank=%s
                """.formatted(
                report.dataset(),
                report.totalCases(),
                report.topK(),
                DECIMAL_FORMAT.format(report.recallAtK()),
                DECIMAL_FORMAT.format(report.precisionAtK()),
                DECIMAL_FORMAT.format(report.mrr()),
                DECIMAL_FORMAT.format(report.ndcgAtK()),
                DECIMAL_FORMAT.format(report.hitRate()),
                DECIMAL_FORMAT.format(report.averageFirstRelevantRank()));
    }

    private RagEvalCaseResult evaluateCase(RagEvalCase testCase, int topK) {
        List<SearchResult> results = knowledgeService.retrieve(testCase.question(), topK);
        Set<String> relevantSources = lowerSet(testCase.expectedSources());
        List<String> relevantTerms = lowerList(testCase.expectedTerms());
        List<RagRetrievedItem> retrieved = new ArrayList<>();
        int firstRelevantRank = 0;
        int relevantCount = 0;
        for (int i = 0; i < results.size(); i++) {
            SearchResult result = results.get(i);
            boolean relevant = isRelevant(result, relevantSources, relevantTerms);
            if (relevant) {
                relevantCount++;
                if (firstRelevantRank == 0) {
                    firstRelevantRank = i + 1;
                }
            }
            retrieved.add(new RagRetrievedItem(
                    i + 1,
                    result.chunkId(),
                    result.source(),
                    result.score(),
                    relevant,
                    preview(result.content())));
        }
        boolean hit = firstRelevantRank > 0;
        double precisionAtK = topK <= 0 ? 0.0 : relevantCount / (double) topK;
        double recallAtK = hit ? 1.0 : 0.0;
        double reciprocalRank = hit ? 1.0 / firstRelevantRank : 0.0;
        double ndcgAtK = ndcg(retrieved);
        return new RagEvalCaseResult(
                testCase.id(),
                testCase.question(),
                safeList(testCase.expectedSources()),
                safeList(testCase.expectedTerms()),
                retrieved,
                hit,
                firstRelevantRank,
                recallAtK,
                precisionAtK,
                reciprocalRank,
                ndcgAtK);
    }

    private List<RagEvalCase> loadDataset(String datasetLocation) {
        try {
            Resource resource = resourceLoader.getResource(datasetLocation);
            try (InputStream inputStream = resource.getInputStream()) {
                return objectMapper.readValue(inputStream, new TypeReference<>() {
                });
            }
        } catch (Exception exception) {
            throw new IllegalArgumentException("Failed to load RAG evaluation dataset: " + datasetLocation, exception);
        }
    }

    private boolean isRelevant(SearchResult result, Set<String> expectedSources, List<String> expectedTerms) {
        String source = result.source() == null ? "" : result.source().toLowerCase(Locale.ROOT);
        if (!expectedSources.isEmpty() && expectedSources.contains(source)) {
            return true;
        }
        String content = result.content() == null ? "" : result.content().toLowerCase(Locale.ROOT);
        return expectedTerms.stream()
                .filter(term -> term.length() >= 2)
                .anyMatch(content::contains);
    }

    private double ndcg(List<RagRetrievedItem> retrieved) {
        double dcg = 0.0;
        int relevant = 0;
        for (int i = 0; i < retrieved.size(); i++) {
            if (retrieved.get(i).relevant()) {
                relevant++;
                dcg += 1.0 / Math.log(i + 2.0);
            }
        }
        if (relevant == 0) {
            return 0.0;
        }
        double ideal = 0.0;
        for (int i = 0; i < relevant; i++) {
            ideal += 1.0 / Math.log(i + 2.0);
        }
        return dcg / ideal;
    }

    private double average(
            List<RagEvalCaseResult> results,
            java.util.function.ToDoubleFunction<RagEvalCaseResult> function,
            double denominator
    ) {
        return results.stream().mapToDouble(function).sum() / denominator;
    }

    private Set<String> lowerSet(List<String> values) {
        return new HashSet<>(lowerList(values));
    }

    private List<String> lowerList(List<String> values) {
        return safeList(values).stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .toList();
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private String preview(String content) {
        if (content == null) {
            return "";
        }
        String compact = content.replaceAll("\\s+", " ").trim();
        return compact.length() > 160 ? compact.substring(0, 160) : compact;
    }
}
