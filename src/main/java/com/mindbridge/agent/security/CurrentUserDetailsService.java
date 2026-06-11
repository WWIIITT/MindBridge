package com.mindbridge.agent.security;

import com.mindbridge.agent.repository.UserAccountRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
/**
 * 登錄認證時的賬號加載服務。
 *
 * <p>項目使用響應式 WebFlux，但 JPA 是阻塞訪問，因此查詢放到 boundedElastic 線程池。</p>
 */
public class CurrentUserDetailsService implements ReactiveUserDetailsService {

    private final UserAccountRepository userAccountRepository;

    public CurrentUserDetailsService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    public Mono<UserDetails> findByUsername(String username) {
        return Mono.fromCallable(() -> userAccountRepository.findByUsername(username)
                        .map(CurrentUser::new)
                        .map(UserDetails.class::cast)
                        .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username)))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
