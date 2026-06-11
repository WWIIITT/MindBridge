package com.mindbridge.agent.dto;

/**
 * 知識庫入庫結果，返回數據來源和切塊數量。
 */
public record KnowledgeIngestResponse(String source, int chunks) {
}
