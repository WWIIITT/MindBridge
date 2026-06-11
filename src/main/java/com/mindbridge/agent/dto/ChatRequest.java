package com.mindbridge.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 學生髮起聊天請求。
 *
 * @param sessionId 爲空時創建新會話；非空時繼續已有會話
 * @param message 學生本輪輸入
 */
public record ChatRequest(
        String sessionId,
        @NotBlank @Size(max = 4000) String message
) {
}
