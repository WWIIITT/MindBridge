package com.mindbridge.agent.service.knowledge;

import com.mindbridge.agent.repository.KnowledgeChunkRepository;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

@Service
/**
 * 內置知識庫初始化服務。
 *
 * <p>首次啓動且數據庫沒有知識切塊時，自動讀取 classpath:knowledge 下的默認文檔。</p>
 */
public class KnowledgeIngestionService {

    private final KnowledgeChunkRepository knowledgeChunkRepository;
    private final KnowledgeService knowledgeService;

    public KnowledgeIngestionService(KnowledgeChunkRepository knowledgeChunkRepository, KnowledgeService knowledgeService) {
        this.knowledgeChunkRepository = knowledgeChunkRepository;
        this.knowledgeService = knowledgeService;
    }

    public void ingestClasspathKnowledgeIfEmpty() {
        if (knowledgeChunkRepository.count() > 0) {
            return;
        }
        try {
            // classpath*: 支持未來從多個 jar 或目錄中合併加載知識文件。
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath*:knowledge/*.*");
            for (Resource resource : resources) {
                String content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
                knowledgeService.ingest(resource.getFilename(), content);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load bundled knowledge base", exception);
        }
    }
}
