package com.mindbridge.agent.service.knowledge.eval;

public record RagRetrievedItem(
        int rank,
        Long chunkId,
        String source,
        double score,
        boolean relevant,
        String preview
) {
}
