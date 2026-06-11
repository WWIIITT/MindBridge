package com.mindbridge.agent.service.ai;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import reactor.core.publisher.Flux;

/**
 * 本地 mock 模型客戶端。
 *
 * <p>用於無模型環境下演示完整業務流程，支持意圖分類、心理評估和簡單回答。</p>
 */
public class HeuristicAiClient implements AiClient {

    @Override
    public String complete(List<AiMessage> messages) {
        String prompt = messages.stream()
                .map(AiMessage::content)
                .reduce("", (left, right) -> left + "\n" + right);
        String input = lastUserMessage(messages);
        if (prompt.contains("intent classifier") || prompt.contains("意圖分類器")) {
            return classify(input);
        }
        if (prompt.contains("strict JSON") || prompt.contains("嚴格 JSON") || prompt.contains("\"emotion\"")) {
            return analyze(input);
        }
        return answer(input, prompt);
    }

    @Override
    public Flux<String> stream(List<AiMessage> messages) {
        String answer = complete(messages);
        return Flux.fromArray(answer.split("(?<=.)"))
                .delayElements(Duration.ofMillis(12));
    }

    private String classify(String input) {
        String normalized = RiskLexicon.normalizeChineseForMatching(input.toLowerCase(Locale.ROOT));
        String current = RiskLexicon.normalizeChineseForMatching(currentInput(input).toLowerCase(Locale.ROOT));
        if (RiskLexicon.hasHighRiskSignal(current)) {
            return "RISK";
        }
        if (RiskLexicon.hasConsultSignal(current) || RiskLexicon.hasConsultSignal(normalized)) {
            return "CONSULT";
        }
        return "CHAT";
    }

    private String analyze(String input) {
        String normalized = RiskLexicon.normalizeChineseForMatching(input.toLowerCase(Locale.ROOT));
        String current = RiskLexicon.normalizeChineseForMatching(currentInput(input).toLowerCase(Locale.ROOT));
        if (RiskLexicon.hasHighRiskSignal(current)) {
            return "{\"emotion\":\"HIGH_RISK\",\"emotionScore\":4.0,\"risk\":\"HIGH\",\"confidence\":0.92,\"summary\":\"檢測到明確的高風險自傷或危險信號\"}";
        }
        if (containsAny(current, "抑鬱", "低落", "壓抑", "崩潰", "難過", "絕望", "depress", "hopeless")) {
            return "{\"emotion\":\"DEPRESSED\",\"emotionScore\":3.2,\"risk\":\"MEDIUM\",\"confidence\":0.82,\"summary\":\"檢測到持續低落或壓抑相關表達\"}";
        }
        if (containsAny(current, "焦慮", "壓力", "睡不着", "失眠", "緊張", "anxious", "stress", "insomnia")) {
            return "{\"emotion\":\"ANXIETY\",\"emotionScore\":2.2,\"risk\":\"LOW\",\"confidence\":0.78,\"summary\":\"檢測到焦慮、壓力或睡眠困擾相關表達\"}";
        }
        if (RiskLexicon.hasConsultSignal(normalized)) {
            return "{\"emotion\":\"ANXIETY\",\"emotionScore\":2.0,\"risk\":\"LOW\",\"confidence\":0.70,\"summary\":\"結合上下文檢測到心理諮詢延續表達\"}";
        }
        return "{\"emotion\":\"NORMAL\",\"emotionScore\":0.0,\"risk\":\"LOW\",\"confidence\":0.70,\"summary\":\"未檢測到明顯心理風險信號\"}";
    }

    private String answer(String input, String prompt) {
        String normalized = RiskLexicon.normalizeChineseForMatching(input.toLowerCase(Locale.ROOT));
        if (RiskLexicon.hasHighRiskSignal(normalized)) {
            return """
                    我會認真對待你剛纔說的這些話。現在最重要的不是把問題講清楚，而是先確保你此刻是安全的。

                    請你先做三件事：第一，離開任何可能讓你傷害自己或他人的物品和環境；第二，馬上聯繫一個現實中能到你身邊的人，比如同學、室友、家人、輔導員或學校心理中心；第三，如果你已經處在馬上會傷害自己或他人的危險裏，請立刻撥打當地緊急救助電話。

                    你不用一個人扛完這一刻。你可以先回復我一個很短的答案：你現在是一個人嗎？身邊有沒有一個可以立刻聯繫到的人？
                    """;
        }
        if (RiskLexicon.hasConsultSignal(normalized) || prompt.contains("檢索知識：")) {
            return consultAnswer(input);
        }
        return """
                我在。你可以把我當作一個校園心理支持助手來用：日常閒聊我會自然回應；如果你聊到壓力、焦慮、睡眠、人際關係或學習困擾，我會先判斷意圖，再結合最近上下文和知識庫給你更具體的建議。

                你現在想聊輕鬆一點的內容，還是想說說最近真正讓你卡住的一件事？
                """;
    }

    private String consultAnswer(String input) {
        String focus = focusFrom(input);
        return """
                我能感覺到這件事已經佔用了你不少精力。先不用急着把它歸結成“我是不是不行”，我們可以先把它拆小一點看：%s。

                你現在可以先做幾件很具體的小事：
                1. 用一兩句話寫下最困擾你的觸發點，儘量區分“發生了什麼”和“我腦子裏正在擔心什麼”。
                2. 給身體一個短暫停頓：慢慢呼氣 6 秒、吸氣 4 秒，重複 3 輪，先把緊繃感降一點。
                3. 今天只選一個能完成的小動作，比如給老師/同學發一條確認信息、洗個熱水澡、或提前 20 分鐘放下手機。
                4. 如果這種狀態已經持續兩週以上，或明顯影響上課、睡眠、飲食，建議儘快聯繫學校心理中心或輔導員。

                我們可以繼續從最具體的地方開始。這個困擾最明顯是在什麼時候出現的？
                """.formatted(focus);
    }

    private String focusFrom(String input) {
        String normalized = RiskLexicon.normalizeChineseForMatching(input.toLowerCase(Locale.ROOT));
        if (containsAny(normalized, "睡不着", "失眠", "睡眠", "insomnia")) {
            return "你提到的睡眠問題可能正在放大白天的疲憊和焦慮";
        }
        if (containsAny(normalized, "考試", "考研", "學習", "掛科", "作業", "論文")) {
            return "你面對的學習或考試壓力需要被拆成可處理的任務，而不是一次性壓在心裏";
        }
        if (containsAny(normalized, "分手", "戀愛", "親密關係", "關係", "室友", "朋友", "社交")) {
            return "關係裏的不確定和消耗很容易讓人反覆想、反覆內耗";
        }
        if (containsAny(normalized, "低落", "抑鬱", "難過", "沒動力", "想哭", "壓抑")) {
            return "你現在的低落感值得被認真看見，而不是被簡單勸成“想開點”";
        }
        if (containsAny(normalized, "焦慮", "緊張", "害怕", "恐懼", "壓力", "煩躁")) {
            return "你身體和腦子都像是在持續警覺，所以會很累";
        }
        return "先抓住一個最讓你難受的場景，比泛泛地處理全部情緒更容易開始";
    }

    private String lastUserMessage(List<AiMessage> messages) {
        for (int index = messages.size() - 1; index >= 0; index--) {
            AiMessage message = messages.get(index);
            if ("user".equals(message.role())) {
                return message.content();
            }
        }
        return messages.isEmpty() ? "" : messages.get(messages.size() - 1).content();
    }

    private String currentInput(String input) {
        String marker = "當前輸入：";
        int index = input.lastIndexOf(marker);
        if (index < 0) {
            return input;
        }
        return input.substring(index + marker.length()).trim();
    }

    private boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
