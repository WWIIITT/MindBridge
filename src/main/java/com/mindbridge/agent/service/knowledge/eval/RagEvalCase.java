package com.mindbridge.agent.service.knowledge.eval;

import java.util.List;

public record RagEvalCase(
        String id,
        String question,
        List<String> expectedSources,
        List<String> expectedTerms
) {
}
