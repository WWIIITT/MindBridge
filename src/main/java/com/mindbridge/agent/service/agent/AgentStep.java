package com.mindbridge.agent.service.agent;

import java.time.Instant;

/**
 * Agent loop 的一步執行軌跡。
 *
 * <p>當前先保留在本輪運行結果中，後續如果要在管理員後臺展示 agent trace，可以直接持久化這個對象。</p>
 */
public record AgentStep(
        int step,
        AgentName agent,
        AgentAction action,
        String observation,
        Instant createdAt
) {
    public static AgentStep of(int step, AgentName agent, AgentDecision decision) {
        return new AgentStep(step, agent, decision.action(), decision.observation(), Instant.now());
    }
}
