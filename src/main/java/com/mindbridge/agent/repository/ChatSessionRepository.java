package com.mindbridge.agent.repository;

import com.mindbridge.agent.domain.ChatSession;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 會話的數據訪問接口。
 */
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    /** 學生繼續對話時，必須校驗會話屬於當前用戶。 */
    Optional<ChatSession> findByPublicIdAndUser_Id(String publicId, Long userId);

    /** 管理員查看會話詳情時需要連同用戶信息一起加載。 */
    @EntityGraph(attributePaths = "user")
    Optional<ChatSession> findByPublicId(String publicId);
}
