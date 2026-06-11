package com.mindbridge.agent.service.agent;

import com.mindbridge.agent.config.MindBridgeProperties;
import com.mindbridge.agent.domain.ChatMessage;
import com.mindbridge.agent.domain.ChatSession;
import com.mindbridge.agent.repository.ChatMessageRepository;
import com.mindbridge.agent.service.PrivacySanitizer;
import com.mindbridge.agent.service.ai.AiClient;
import com.mindbridge.agent.service.ai.AiMessage;
import com.mindbridge.agent.service.memory.ShortTermMemoryService;
import com.mindbridge.agent.service.memory.ShortTermMemoryService.MemoryMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 記憶 Agent。
 *
 * <p>優先讀取 Redis 短期記憶；短期記憶過期時，從 MySQL 長期記憶恢復最近上下文。</p>
 */
@Component
public class MemoryAgent implements MindBridgeAgent {

    private final ChatMessageRepository chatMessageRepository;
    private final ShortTermMemoryService shortTermMemoryService;
    private final MindBridgeProperties properties;
    private final PrivacySanitizer privacySanitizer;
    private final AiClient aiClient;

    public MemoryAgent(
            ChatMessageRepository chatMessageRepository,
            ShortTermMemoryService shortTermMemoryService,
            MindBridgeProperties properties,
            PrivacySanitizer privacySanitizer,
            AiClient aiClient
    ) {
        this.chatMessageRepository = chatMessageRepository;
        this.shortTermMemoryService = shortTermMemoryService;
        this.properties = properties;
        this.privacySanitizer = privacySanitizer;
        this.aiClient = aiClient;
    }

    @Override
    public AgentName name() {
        return AgentName.MEMORY_AGENT;
    }

    @Override
    public boolean supports(AgentContext context) {
        return !context.memoryLoaded();
    }

    @Override
    public AgentDecision act(AgentContext context) {
        List<MemoryMessage> redisHistory = shortTermMemoryService.recent(context.session().getPublicId());
        List<AiMessage> previousHistory;
        String source;
        if (!redisHistory.isEmpty()) {
            previousHistory = redisHistory.stream()
                    .map(this::toAiMessage)
                    .toList();
            source = "Redis";
        } else {
            List<ChatMessage> databaseHistory = recentHistory(context.session());
            shortTermMemoryService.refresh(context.session().getPublicId(), databaseHistory.stream()
                    .map(message -> new MemoryMessage(message.getRole(), message.getContent()))
                    .toList());
            previousHistory = databaseHistory.stream()
                    .map(this::toAiMessage)
                    .toList();
            source = "MySQL";
        }

        context.setPreviousHistory(previousHistory);
        context.setModelHistory(withCurrentUser(previousHistory, context.modelInput()));
        context.setMemoryBrief(summarizeMemory(previousHistory, context.modelInput()));
        context.markMemoryLoaded();
        return AgentDecision.continueWith(
                AgentAction.READ_MEMORY,
                "%s loaded %d messages; memory brief prepared".formatted(source, previousHistory.size()));
    }

    private List<ChatMessage> recentHistory(ChatSession session) {
        List<ChatMessage> history = chatMessageRepository.findTop20BySession_IdOrderByCreatedAtDesc(session.getId());
        Collections.reverse(history);
        return history;
    }

    private List<AiMessage> withCurrentUser(List<AiMessage> previousHistory, String currentInput) {
        List<AiMessage> history = new ArrayList<>(previousHistory);
        history.add(AiMessage.user(currentInput));
        int limit = Math.max(2, properties.getChat().getHistoryLimit() * 2);
        return history.stream()
                .skip(Math.max(0, history.size() - limit))
                .toList();
    }

    private String summarizeMemory(List<AiMessage> history, String currentInput) {
        if (history.isEmpty()) {
            return "無相關歷史記憶。";
        }
        try {
            String summary = aiClient.complete(List.of(
                    AiMessage.system("""
                            你是 MindBridge 的 MemoryAgent。
                            你的任務是從最近對話中提取對當前輸入有用的短期/長期記憶。
                            只輸出 1-3 條中文要點，不要輸出風險等級、診斷結論或後臺標籤。
                            如果歷史與當前輸入無關，只輸出：無相關歷史記憶。
                            """),
                    AiMessage.user("""
                            當前輸入：
                            %s

                            最近歷史：
                            %s
                            """.formatted(currentInput, formatHistory(history)))
            )).trim();
            return summary.isBlank() ? "無相關歷史記憶。" : shorten(summary, 400);
        } catch (Exception ignored) {
            return "無相關歷史記憶。";
        }
    }

    private String formatHistory(List<AiMessage> history) {
        return String.join("\n", history.stream()
                .skip(Math.max(0, history.size() - 12))
                .map(message -> message.role() + ": " + message.content())
                .toList());
    }

    private String shorten(String value, int maxLength) {
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    private AiMessage toAiMessage(ChatMessage chatMessage) {
        String content = privacySanitizer.sanitize(chatMessage.getContent());
        return switch (chatMessage.getRole()) {
            case ASSISTANT -> AiMessage.assistant(content);
            case SYSTEM -> AiMessage.system(content);
            case USER -> AiMessage.user(content);
        };
    }

    private AiMessage toAiMessage(MemoryMessage memoryMessage) {
        String content = privacySanitizer.sanitize(memoryMessage.content());
        return switch (memoryMessage.role()) {
            case ASSISTANT -> AiMessage.assistant(content);
            case SYSTEM -> AiMessage.system(content);
            case USER -> AiMessage.user(content);
        };
    }
}
