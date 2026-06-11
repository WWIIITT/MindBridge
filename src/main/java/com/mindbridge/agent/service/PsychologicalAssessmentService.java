package com.mindbridge.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindbridge.agent.domain.EmotionLabel;
import com.mindbridge.agent.domain.RiskLevel;
import com.mindbridge.agent.service.ai.AiClient;
import com.mindbridge.agent.service.ai.AiMessage;
import com.mindbridge.agent.service.ai.PromptTemplates;
import com.mindbridge.agent.service.ai.RiskLexicon;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
/**
 * 後臺心理狀態評估服務。
 *
 * <p>結合高風險詞庫、模型結構化輸出和關鍵詞兜底，生成報告所需的情緒與風險字段。</p>
 */
public class PsychologicalAssessmentService {

    private final AiClient aiClient;
    private final ObjectMapper objectMapper;

    public PsychologicalAssessmentService(AiClient aiClient, ObjectMapper objectMapper) {
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
    }

    public PsychologyAssessment assess(String input) {
        return assess(input, List.of());
    }

    public PsychologyAssessment assess(String input, List<AiMessage> history) {
        // 高風險詞庫是硬規則，優先於模型判斷，保證明顯自傷/傷人信號不會被漏掉。
        if (RiskLexicon.hasHighRiskSignal(input.toLowerCase())) {
            return new PsychologyAssessment(
                    EmotionLabel.HIGH_RISK,
                    4.0,
                    RiskLevel.HIGH,
                    0.95,
                    "Explicit high-risk signal detected.");
        }
        try {
            String raw = aiClient.complete(PromptTemplates.psychologyPrompt(history, input));
            return normalize(parseJson(raw));
        } catch (Exception ignored) {
            // 模型輸出格式異常或調用失敗時，使用關鍵詞兜底，保證報告鏈路仍可運行。
            return heuristic(input);
        }
    }

    private PsychologyAssessment parseJson(String raw) throws Exception {
        String json = raw.trim();
        // 兼容模型在 JSON 前後額外輸出少量文本的情況，只截取最外層 JSON 對象。
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start >= 0 && end > start) {
            json = json.substring(start, end + 1);
        }
        JsonNode node = objectMapper.readTree(json);
        EmotionLabel emotion = EmotionLabel.valueOf(node.path("emotion").asText("NORMAL").toUpperCase());
        double score = node.path("emotionScore").asDouble(scoreForEmotion(emotion));
        RiskLevel risk = RiskLevel.valueOf(node.path("risk").asText(riskFromScore(score).name()).toUpperCase());
        double confidence = node.path("confidence").asDouble(0.75);
        String summary = node.path("summary").asText("Model assessment.");
        return new PsychologyAssessment(emotion, score, risk, confidence, summary);
    }

    private PsychologyAssessment normalize(PsychologyAssessment assessment) {
        // 風險等級取模型等級和分數推導等級中更高的一方，降低低估風險的概率。
        RiskLevel scoreRisk = riskFromScore(assessment.emotionScore());
        RiskLevel risk = assessment.risk().ordinal() > scoreRisk.ordinal() ? assessment.risk() : scoreRisk;
        if (assessment.emotion() == EmotionLabel.HIGH_RISK) {
            risk = RiskLevel.HIGH;
        }
        return new PsychologyAssessment(
                assessment.emotion(),
                assessment.emotionScore(),
                risk,
                clamp(assessment.confidence(), 0.0, 1.0),
                assessment.summary());
    }

    private PsychologyAssessment heuristic(String input) {
        String normalized = RiskLexicon.normalizeChineseForMatching(input.toLowerCase());
        if (containsAny(normalized, "抑鬱", "低落", "壓抑", "崩潰", "難過", "depress", "hopeless")) {
            return new PsychologyAssessment(EmotionLabel.DEPRESSED, 3.1, RiskLevel.MEDIUM, 0.75, "Low mood keywords detected.");
        }
        if (containsAny(normalized, "焦慮", "壓力", "睡不着", "失眠", "anxious", "stress", "insomnia")) {
            return new PsychologyAssessment(EmotionLabel.ANXIETY, 2.2, RiskLevel.LOW, 0.72, "Anxiety or pressure keywords detected.");
        }
        return new PsychologyAssessment(EmotionLabel.NORMAL, 0.0, RiskLevel.LOW, 0.66, "No obvious risk signal.");
    }

    private RiskLevel riskFromScore(double score) {
        if (score >= 4.0) {
            return RiskLevel.HIGH;
        }
        if (score >= 3.0) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.LOW;
    }

    private double scoreForEmotion(EmotionLabel emotion) {
        return switch (emotion) {
            case HIGH_RISK -> 4.0;
            case DEPRESSED -> 3.0;
            case ANXIETY -> 2.0;
            case NORMAL -> 0.0;
        };
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
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
