package com.mindbridge.agent.service.ai;

import java.util.List;

/**
 * 風險和諮詢關鍵詞詞庫。
 *
 * <p>它是模型判斷前的硬規則兜底，主要用於快速識別明確危險信號。</p>
 */
public final class RiskLexicon {

    private static final List<String> HIGH_RISK_WORDS = List.of(
            "不想活", "活不下去", "撐不下去", "自殺", "自殘", "輕生", "結束生命", "結束這一切",
            "傷害自己", "傷人", "殺了", "想死", "去死", "沒有活着的意義", "不想存在", "消失算了",
            "suicide", "kill myself", "self harm", "end my life", "hurt myself", "hurt others", "want to die"
    );

    private static final List<String> CONSULT_WORDS = List.of(
            "焦慮", "壓力", "壓抑", "抑鬱", "低落", "失眠", "睡不着", "崩潰", "難過", "孤獨",
            "情緒", "心理", "心理諮詢", "諮詢師", "心累", "煩躁", "害怕", "恐懼", "內耗", "想哭",
            "不開心", "沒動力", "痛苦", "沮喪", "絕望", "無助", "喘不過氣", "panic attack",
            "anxiety", "anxious", "stress", "depress", "sad", "insomnia", "panic", "lonely", "breakup"
    );

    private RiskLexicon() {
    }

    public static boolean hasHighRiskSignal(String text) {
        return containsAny(text, HIGH_RISK_WORDS);
    }

    public static boolean hasConsultSignal(String text) {
        return containsAny(text, CONSULT_WORDS);
    }

    private static boolean containsAny(String text, List<String> words) {
        String normalized = normalizeChineseForMatching(text);
        for (String word : words) {
            if (normalized.contains(word)) {
                return true;
            }
        }
        return false;
    }

    public static String normalizeChineseForMatching(String text) {
        return text
                .replace('\u4e3a', '\u7232')
                .replace('\u4e1a', '\u696d')
                .replace('\u4e49', '\u7fa9')
                .replace('\u4e48', '\u9ebc')
                .replace('\u4e60', '\u7fd2')
                .replace('\u4e71', '\u4e82')
                .replace('\u4eb2', '\u89aa')
                .replace('\u4f24', '\u50b7')
                .replace('\u5173', '\u95dc')
                .replace('\u5199', '\u5beb')
                .replace('\u52a8', '\u52d5')
                .replace('\u538b', '\u58d3')
                .replace('\u53d8', '\u8b8a')
                .replace('\u540e', '\u5f8c')
                .replace('\u54a8', '\u8aee')
                .replace('\u5408', '\u5408')
                .replace('\u5458', '\u54e1')
                .replace('\u56f0', '\u56f0')
                .replace('\u5b66', '\u5b78')
                .replace('\u5e08', '\u5e2b')
                .replace('\u5e2e', '\u5e6b')
                .replace('\u5e93', '\u5eab')
                .replace('\u5f00', '\u958b')
                .replace('\u5f20', '\u5f35')
                .replace('\u603b', '\u7e3d')
                .replace('\u604b', '\u6200')
                .replace('\u60e7', '\u61fc')
                .replace('\u6218', '\u6230')
                .replace('\u636e', '\u64da')
                .replace('\u6302', '\u639b')
                .replace('\u6740', '\u6bba')
                .replace('\u6765', '\u4f86')
                .replace('\u6781', '\u6975')
                .replace('\u6b8b', '\u6b98')
                .replace('\u6c14', '\u6c23')
                .replace('\u6ca1', '\u6c92')
                .replace('\u6e83', '\u6f70')
                .replace('\u70e6', '\u7169')
                .replace('\u7231', '\u611b')
                .replace('\u72ec', '\u7368')
                .replace('\u7d27', '\u7dca')
                .replace('\u7ed3', '\u7d50')
                .replace('\u7edd', '\u7d55')
                .replace('\u7eea', '\u7dd2')
                .replace('\u7f16', '\u7de8')
                .replace('\u7f51', '\u7db2')
                .replace('\u7ffb', '\u7ffb')
                .replace('\u7ebf', '\u7dda')
                .replace('\u8bae', '\u8b70')
                .replace('\u8ba9', '\u8b93')
                .replace('\u8bba', '\u8ad6')
                .replace('\u8bd5', '\u8a66')
                .replace('\u8be2', '\u8a62')
                .replace('\u8bd1', '\u8b6f')
                .replace('\u8be5', '\u8a72')
                .replace('\u8bed', '\u8a9e')
                .replace('\u8bf4', '\u8aaa')
                .replace('\u8c03', '\u8abf')
                .replace('\u8d44', '\u8cc7')
                .replace('\u8d77', '\u8d77')
                .replace('\u8f7b', '\u8f15')
                .replace('\u8f91', '\u8f2f')
                .replace('\u8fd9', '\u9019')
                .replace('\u8fc7', '\u904e')
                .replace('\u9009', '\u9078')
                .replace('\u9002', '\u9069')
                .replace('\u9020', '\u9020')
                .replace('\u90c1', '\u9b31')
                .replace('\u91cc', '\u88cf')
                .replace('\u94fe', '\u93c8')
                .replace('\u9519', '\u932f')
                .replace('\u95e8', '\u9580')
                .replace('\u96be', '\u96e3')
                .replace('\u8350', '\u85a6')
                .replace('\u9879', '\u9805')
                .replace('\u9898', '\u984c')
                .replace('\u98ce', '\u98a8')
                .replace('\u996e', '\u98f2')
                .replace('\u9a8c', '\u9a57')
                .replace('\u6491', '\u64d4')
                .replace('\u8651', '\u616e')
                .replace('\u7cfb', '\u4fc2')
                .replace('\u7b80', '\u7c21')
                .replace('\u7801', '\u78bc')
                .replace('\u79cd', '\u7a2e')
                .replace('\u79bb', '\u96e2')
                .replace('\u7ecf', '\u7d93')
                .replace('\u7ed9', '\u7d66')
                .replace('\u80af', '\u80af')
                .replace('\u8111', '\u8166')
                .replace('\u89e3', '\u89e3')
                .replace('\u89e6', '\u89f8')
                .replace('\u8ba1', '\u8a08')
                .replace('\u8bc4', '\u8a55')
                .replace('\u8bc6', '\u8b58')
                .replace('\u8bca', '\u8a3a')
                .replace('\u91cf', '\u91cf')
                .replace('\u957f', '\u9577');
    }
}
