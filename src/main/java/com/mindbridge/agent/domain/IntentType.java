package com.mindbridge.agent.domain;

/**
 * 用戶輸入的業務意圖。
 *
 * <p>CHAT 走普通對話，CONSULT/RISK 才進入心理支持、RAG 和報告鏈路。</p>
 */
public enum IntentType {
    CHAT,
    CONSULT,
    RISK
}
