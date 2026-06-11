package com.mindbridge.agent.service;

import com.mindbridge.agent.domain.EmotionLabel;
import com.mindbridge.agent.domain.RiskLevel;

/**
 * 一次後臺心理狀態評估結果。
 *
 * <p>該對象只在服務端報告和工具鏈中使用，不作爲學生端消息內容。</p>
 */
public record PsychologyAssessment(
        EmotionLabel emotion,
        double emotionScore,
        RiskLevel risk,
        double confidence,
        String summary
) {
}
