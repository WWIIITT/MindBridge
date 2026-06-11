package com.mindbridge.agent.service.knowledge;

import java.util.List;

/**
 * 文本向量化接口。
 *
 * <p>KnowledgeService 不直接依賴具體 embedding 服務，方便後續替換實現。</p>
 */
public interface EmbeddingClient {

    List<Double> embed(String text);

    String modelName();
}
