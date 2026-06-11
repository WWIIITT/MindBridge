package com.mindbridge.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理員通過 JSON 追加知識庫內容的請求體。
 */
public record KnowledgeIngestRequest(
        @NotBlank @Size(max = 180) String source,
        @NotBlank String content
) {
}
