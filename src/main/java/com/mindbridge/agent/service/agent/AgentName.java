package com.mindbridge.agent.service.agent;

/**
 * MindBridge 內部專業智能體名稱。
 *
 * <p>多智能體協作採用 Supervisor 統籌、專家 Agent 分工的方式，避免把所有職責繼續堆在一個服務裏。</p>
 */
public enum AgentName {
    MEMORY_AGENT,
    SUPERVISOR_AGENT,
    KNOWLEDGE_AGENT,
    RISK_GUARDIAN_AGENT,
    COMPANION_AGENT,
    COUNSELOR_AGENT
}
