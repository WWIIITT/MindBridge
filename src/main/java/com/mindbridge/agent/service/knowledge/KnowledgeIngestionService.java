package com.mindbridge.agent.service.knowledge;

import com.mindbridge.agent.repository.KnowledgeChunkRepository;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

@Service
/**
 * 内置知识库初始化服务。
 *
 * <p>首次启动且数据库没有知识切块时，自动读取 classpath:knowledge 下的默认文档。</p>
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
            // classpath*: 支持未来从多个 jar 或目录中合并加载知识文件。
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
