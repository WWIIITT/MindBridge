package com.mindbridge.agent.service.knowledge.eval;

import java.util.List;

public record RagEvalCaseResult(
        String id,
        String question,
        List<String> expectedSources,
        List<String> expectedTerms,
        List<RagRetrievedItem> retrieved,
        boolean hit,
        int firstRelevantRank,
        double recallAtK,
        double precisionAtK,
        double reciprocalRank,
        double ndcgAtK
) {
}
