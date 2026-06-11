package com.mindbridge.agent.service;

import com.mindbridge.agent.domain.IntentType;
import com.mindbridge.agent.service.ai.AiClient;
import com.mindbridge.agent.service.ai.AiMessage;
import com.mindbridge.agent.service.ai.PromptTemplates;
import com.mindbridge.agent.service.ai.RiskLexicon;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
/**
 * 用戶意圖分類服務。
 *
 * <p>把每輪輸入路由到普通聊天、心理諮詢或高風險處理鏈路。</p>
 */
public class IntentClassifier {

    private static final List<String> GENERAL_TASK_WORDS = List.of(
            "java", "python", "javascript", "代碼", "編程", "程序", "算法", "數據庫", "spring", "maven",
            "前端", "後端", "項目", "接口", "bug", "報錯", "作業", "論文", "翻譯", "總結", "解釋",
            "怎麼寫", "如何", "是什麼", "爲什麼", "給我", "幫我", "推薦", "查詢", "天氣", "路線"
    );

    private final AiClient aiClient;

    public IntentClassifier(AiClient aiClient) {
        this.aiClient = aiClient;
    }

    public IntentType classify(String input) {
        return classify(input, List.of());
    }

    public IntentType classify(String input, List<AiMessage> history) {
        String normalized = RiskLexicon.normalizeChineseForMatching(input.toLowerCase(Locale.ROOT));
        // 高風險表達優先級最高，不交給普通任務規則覆蓋。
        if (RiskLexicon.hasHighRiskSignal(normalized)) {
            return IntentType.RISK;
        }
        // 學習、編程、作業等明確普通任務直接走 CHAT，避免誤觸發後臺評估。
        if (isClearlyGeneralTask(normalized)) {
            return IntentType.CHAT;
        }
        try {
            String label = aiClient.complete(PromptTemplates.intentPrompt(history, input)).trim().toUpperCase();
            if (label.contains("RISK")) {
                return IntentType.RISK;
            }
            if (label.contains("CONSULT")) {
                return IntentType.CONSULT;
            }
            if (label.contains("CHAT")) {
                return IntentType.CHAT;
            }
        } catch (Exception ignored) {
            // Keyword fallback keeps the route deterministic when the model is unavailable.
        }
        if (RiskLexicon.hasConsultSignal(normalized) || hasRecentConsultContext(history)) {
            return IntentType.CONSULT;
        }
        return IntentType.CHAT;
    }

    private boolean isClearlyGeneralTask(String input) {
        if (RiskLexicon.hasConsultSignal(input)) {
            return false;
        }
        return GENERAL_TASK_WORDS.stream().anyMatch(input::contains);
    }

    private boolean hasRecentConsultContext(List<AiMessage> history) {
        if (history == null || history.isEmpty()) {
            return false;
        }
        return history.stream()
                .skip(Math.max(0, history.size() - 6))
                .map(message -> RiskLexicon.normalizeChineseForMatching(message.content().toLowerCase(Locale.ROOT)))
                .anyMatch(RiskLexicon::hasConsultSignal);
    }
}
