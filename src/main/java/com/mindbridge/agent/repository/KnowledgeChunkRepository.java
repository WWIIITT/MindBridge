package com.mindbridge.agent.repository;

import com.mindbridge.agent.domain.KnowledgeChunk;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 知識庫切塊的數據訪問接口。
 */
public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, Long> {

    List<KnowledgeChunk> findTop20BySourceOrderByCreatedAtDesc(String source);

    /** 檢索命中後取相鄰切塊，用於補齊上下文。 */
    List<KnowledgeChunk> findBySourceAndSourceIndexBetweenOrderBySourceIndexAsc(
            String source,
            int startIndex,
            int endIndex
    );

    /** 同名文件重新上傳時清理舊切塊。 */
    void deleteBySource(String source);
}
