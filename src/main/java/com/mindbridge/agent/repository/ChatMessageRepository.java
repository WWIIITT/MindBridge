package com.mindbridge.agent.repository;

import com.mindbridge.agent.domain.ChatMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 聊天消息的數據訪問接口。
 */
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /** 取最近消息用於模型上下文，Service 層會再反轉爲時間正序。 */
    List<ChatMessage> findTop20BySession_IdOrderByCreatedAtDesc(Long sessionId);

    /** 管理員點開記錄時讀取完整會話。 */
    List<ChatMessage> findBySession_PublicIdOrderByCreatedAtAsc(String publicId);
}
