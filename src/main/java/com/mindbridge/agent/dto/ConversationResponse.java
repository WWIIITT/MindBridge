package com.mindbridge.agent.dto;

import com.mindbridge.agent.domain.ChatMessage;
import com.mindbridge.agent.domain.ChatSession;
import java.time.Instant;
import java.util.List;

/**
 * 管理員查看完整會話的響應體。
 */
public record ConversationResponse(
        String sessionId,
        String title,
        Long userId,
        String username,
        String displayName,
        Instant createdAt,
        Instant updatedAt,
        List<ConversationMessageResponse> messages
) {
    public static ConversationResponse from(ChatSession session, List<ChatMessage> messages) {
        return new ConversationResponse(
                session.getPublicId(),
                session.getTitle(),
                session.getUser().getId(),
                session.getUser().getUsername(),
                session.getUser().getDisplayName(),
                session.getCreatedAt(),
                session.getUpdatedAt(),
                messages.stream()
                        .map(ConversationMessageResponse::from)
                        .toList());
    }
}
