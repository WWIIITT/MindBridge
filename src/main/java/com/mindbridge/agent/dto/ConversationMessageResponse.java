package com.mindbridge.agent.dto;

import com.mindbridge.agent.domain.ChatMessage;
import com.mindbridge.agent.domain.MessageRole;
import java.time.Instant;

/**
 * 管理員查看完整會話時的單條消息響應。
 */
public record ConversationMessageResponse(
        Long id,
        MessageRole role,
        String content,
        Instant createdAt
) {
    public static ConversationMessageResponse from(ChatMessage message) {
        return new ConversationMessageResponse(
                message.getId(),
                message.getRole(),
                message.getContent(),
                message.getCreatedAt());
    }
}
