package com.mindbridge.agent.service;

import org.springframework.stereotype.Service;

@Service
/**
 * 輸入隱私脫敏服務。
 *
 * <p>對發送給模型和評估鏈路的文本做輕量脫敏，降低敏感標識進入上下文的概率。</p>
 */
public class PrivacySanitizer {

    public String sanitize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        // 模型側只需要語義，不需要手機號、學號、證件號等敏感標識。
        String sanitized = text;
        sanitized = sanitized.replaceAll("1[3-9]\\d{9}", "[手機號]");
        sanitized = sanitized.replaceAll("(?i)(學號|student\\s*id)[:：\\s]*[A-Za-z0-9_-]{6,20}", "$1:[學號]");
        sanitized = sanitized.replaceAll("(?i)(身份證|id\\s*card)[:：\\s]*[0-9xX]{15,18}", "$1:[證件號]");
        sanitized = sanitized.replaceAll("我叫[\\u4e00-\\u9fa5]{2,4}", "我叫[姓名]");
        sanitized = sanitized.replaceAll("我是[\\u4e00-\\u9fa5]{2,4}", "我是[姓名]");
        return sanitized;
    }
}
