package com.mindbridge.agent.service.knowledge;

import java.util.HashMap;
import java.util.Map;

/**
 * 本地輕量文本向量器。
 *
 * <p>在沒有外部向量服務時，用詞頻和中文 bigram 提供一個可用的檢索兜底。</p>
 */
public class TokenVectorizer {

    public Map<String, Double> vectorize(String text) {
        Map<String, Double> vector = new HashMap<>();
        String normalized = text.toLowerCase().replaceAll("\\s+", " ");
        for (String token : normalized.split("[^\\p{IsHan}a-z0-9]+")) {
            if (!token.isBlank()) {
                vector.merge(token, 1.0, Double::sum);
            }
        }
        for (int i = 0; i < normalized.length() - 1; i++) {
            char first = normalized.charAt(i);
            char second = normalized.charAt(i + 1);
            if (isChinese(first) && isChinese(second)) {
                vector.merge("" + first + second, 1.0, Double::sum);
            }
        }
        return vector;
    }

    public double cosine(String left, String right) {
        Map<String, Double> a = vectorize(left);
        Map<String, Double> b = vectorize(right);
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        double dot = 0.0;
        for (Map.Entry<String, Double> entry : a.entrySet()) {
            dot += entry.getValue() * b.getOrDefault(entry.getKey(), 0.0);
        }
        return dot / (norm(a) * norm(b));
    }

    private double norm(Map<String, Double> vector) {
        double sum = 0.0;
        for (double value : vector.values()) {
            sum += value * value;
        }
        return Math.sqrt(sum);
    }

    private boolean isChinese(char value) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(value);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B;
    }
}
