package com.mindbridge.agent.controller;

import com.mindbridge.agent.dto.ChatRequest;
import com.mindbridge.agent.dto.ChatStreamEvent;
import com.mindbridge.agent.security.CurrentUser;
import com.mindbridge.agent.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/chat")
/**
 * 學生聊天接口。
 *
 * <p>只允許學生賬號發起對話，返回 SSE 流式事件供前端逐字顯示。</p>
 */
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatStreamEvent>> stream(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody ChatRequest request
    ) {
        // 管理員後臺只用於查看記錄和工具狀態，不能以管理員身份生成學生對話。
        boolean isAdmin = currentUser.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        if (isAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "管理員賬號只能查看後臺記錄，不能發起學生對話。");
        }
        return chatService.streamChat(currentUser.getId(), request);
    }
}
