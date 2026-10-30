package com.pte.chat.internal.controller;

import com.pte.chat.internal.constant.ChatConstants;
import com.pte.chat.internal.dto.response.ChatHistoryDeletedResponse;
import com.pte.chat.internal.service.privacy.ChatStudentDataService;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Deletes the caller's own chats. It does not check practice entitlement: a lapsed student may still take data back. */
@RestController
@RequestMapping(ChatConstants.CHAT_PATH + ChatConstants.HISTORY_PATH)
@PreAuthorize("hasRole('STUDENT')")
@ConditionalOnProperty(name = ChatConstants.PROPERTY_ENABLED, havingValue = "true")
public class ChatHistoryController {

    private final ChatStudentDataService dataService;

    public ChatHistoryController(ChatStudentDataService dataService) {
        this.dataService = dataService;
    }

    @DeleteMapping
    public ApiResponse<ChatHistoryDeletedResponse> deleteMine() {
        return ApiResponse.success(new ChatHistoryDeletedResponse(
                dataService.deleteHistory(CurrentUserContext.required().userId())));
    }
}
