package com.mindbridge.agent.service.agent;

import com.mindbridge.agent.domain.ChatSession;
import com.mindbridge.agent.domain.UserAccount;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * MindBridge Agent Loop 運行時。
 *
 * <p>每輪學生輸入都會進入有限步循環：讀取記憶、主控路由、知識檢索、風險守護和回覆規劃。
 * 這裏不是無限自主循環，而是受步數限制的安全 agent loop，適合心理安全場景。</p>
 */
@Service
public class AgentRuntimeService {

    private static final int MAX_STEPS = 8;

    private final List<MindBridgeAgent> agents;

    public AgentRuntimeService(
            MemoryAgent memoryAgent,
            SupervisorAgent supervisorAgent,
            KnowledgeAgent knowledgeAgent,
            RiskGuardianAgent riskGuardianAgent,
            CompanionAgent companionAgent,
            CounselorAgent counselorAgent
    ) {
        // 順序就是 Supervisor 架構下的協作優先級；每個 Agent 通過 supports 判斷是否該接手。
        this.agents = List.of(
                memoryAgent,
                supervisorAgent,
                knowledgeAgent,
                riskGuardianAgent,
                companionAgent,
                counselorAgent);
    }

    public AgentRunResult run(UserAccount user, ChatSession session, String originalInput, String modelInput) {
        AgentContext context = new AgentContext(user, session, originalInput, modelInput);
        for (int step = 1; step <= MAX_STEPS && !context.finished(); step++) {
            MindBridgeAgent agent = nextAgent(context);
            AgentDecision decision = agent.act(context);
            context.addStep(AgentStep.of(step, agent.name(), decision));
            if (decision.complete()) {
                context.finish();
            }
        }
        return AgentRunResult.from(context);
    }

    private MindBridgeAgent nextAgent(AgentContext context) {
        return agents.stream()
                .filter(agent -> agent.supports(context))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No agent can handle current context."));
    }
}
