package com.mindbridge.agent.service.knowledge.eval;

import java.time.Instant;
import java.util.List;

public record RagEvalReport(
        Instant evaluatedAt,
        String dataset,
        int topK,
        int totalCases,
        double recallAtK,
        double precisionAtK,
        double mrr,
        double ndcgAtK,
        double hitRate,
        double averageFirstRelevantRank,
        List<RagEvalCaseResult> cases
) {
}
