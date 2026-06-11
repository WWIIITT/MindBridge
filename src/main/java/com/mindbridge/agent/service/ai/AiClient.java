package com.mindbridge.agent.service.ai;

import java.util.List;
import reactor.core.publisher.Flux;

/**
 * 模型調用統一接口。
 *
 * <p>業務層通過 complete 做分類/評估，通過 stream 做學生端流式回答。</p>
 */
public interface AiClient {

    String complete(List<AiMessage> messages);

    Flux<String> stream(List<AiMessage> messages);
}
