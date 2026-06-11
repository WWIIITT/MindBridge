package com.mindbridge.agent.service.agent;

/**
 * MindBridge 專業 Agent 接口。
 *
 * <p>每個 Agent 只負責一個清晰職責，由 AgentRuntimeService 按上下文狀態循環選擇下一步。</p>
 */
public interface MindBridgeAgent {

    AgentName name();

    boolean supports(AgentContext context);

    AgentDecision act(AgentContext context);
}
