package com.mindbridge.agent.repository;

import com.mindbridge.agent.domain.UserAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 用戶賬號的數據訪問接口。
 */
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    /** 登錄認證時按用戶名加載賬號。 */
    Optional<UserAccount> findByUsername(String username);
}
