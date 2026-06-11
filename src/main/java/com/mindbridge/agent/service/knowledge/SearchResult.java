package com.mindbridge.agent.service.knowledge;

/**
 * RAG 檢索結果。
 *
 * @param chunkId 數據庫切塊 id，外部檢索結果沒有 id 時可以爲空
 * @param source 知識來源文件或來源名
 * @param content 命中的文本內容
 * @param score 檢索相關性分數，越高越相關
 */
public record SearchResult(Long chunkId, String source, String content, double score) {
}
