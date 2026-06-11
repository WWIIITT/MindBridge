package com.mindbridge.agent;

import com.mindbridge.agent.config.MindBridgeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * MindBridge 後端啓動入口。
 *
 * <p>應用啓動後會加載配置、初始化演示賬號和知識庫，並開放聊天、後臺記錄、知識庫上傳等接口。</p>
 */
@SpringBootApplication
@EnableConfigurationProperties(MindBridgeProperties.class)
public class AgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
    }
}
