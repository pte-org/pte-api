package com.pte.chat.internal.controller;

import com.pte.chat.internal.constant.ChatConstants;
import com.pte.chat.internal.dto.request.ChatMessageRequest;
import com.pte.chat.internal.dto.response.ChatMessageResponse;
import com.pte.chat.internal.dto.response.ChatQuotaResponse;
import com.pte.chat.internal.service.ChatHistoryEntry;
import com.pte.chat.internal.service.ChatReply;
import com.pte.chat.internal.service.ChatSendCommand;
import com.pte.chat.internal.service.ChatService;
import com.pte.chat.internal.service.quota.ChatQuotaSnapshot;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ChatConstants.CHAT_PATH)
@PreAuthorize("hasRole('STUDENT')")
@ConditionalOnProperty(name = ChatConstants.PROPERTY_ENABLED, havingValue = "true")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping(ChatConstants.MESSAGES_PATH)
    public ApiResponse<ChatMessageResponse> send(@Valid @RequestBody ChatMessageRequest request) {
        CurrentUser caller = CurrentUserContext.required();
        List<ChatHistoryEntry> history = request.history() == null ? null : request.history().stream()
                .map(item -> item == null ? null : new ChatHistoryEntry(item.role(), item.text())).toList();
        ChatReply reply = chatService.send(caller, new ChatSendCommand(request.message(), request.taskType(),
                request.language(), request.exercisePrompt(), history));
        return ApiResponse.success(new ChatMessageResponse(reply.markdown(), reply.onTopic(),
                reply.quota().remaining(), reply.quota().limit(), reply.quota().resetAt()));
    }

    @GetMapping(ChatConstants.QUOTA_PATH)
    public ApiResponse<ChatQuotaResponse> quota() {
        ChatQuotaSnapshot snapshot = chatService.quota(CurrentUserContext.required());
        return ApiResponse.success(new ChatQuotaResponse(snapshot.used(), snapshot.remaining(), snapshot.limit(),
                snapshot.resetAt()));
    }
}
