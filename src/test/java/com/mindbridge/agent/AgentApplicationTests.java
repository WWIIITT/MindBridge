package com.mindbridge.agent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:mindbridge-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "mindbridge.ai.provider=mock"
})
class AgentApplicationTests {

    @Test
    void contextLoads() {
    }
}
