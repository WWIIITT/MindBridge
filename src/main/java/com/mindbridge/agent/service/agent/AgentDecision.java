package com.mindbridge.agent.service.agent;

/**
 * 單個 Agent 執行一步後返回的決策摘要。
 *
 * @param action 當前執行的動作
 * @param observation 動作結果摘要，用於後續調試和可視化 trace
 * @param complete 是否結束本輪 agent loop
 */
public record AgentDecision(
        AgentAction action,
        String observation,
        boolean complete
) {
    public static AgentDecision continueWith(AgentAction action, String observation) {
        return new AgentDecision(action, observation, false);
    }

    public static AgentDecision finish(AgentAction action, String observation) {
        return new AgentDecision(action, observation, true);
    }
}
