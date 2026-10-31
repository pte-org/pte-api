package com.pte.chat.internal.exception;

import com.pte.chat.internal.constant.ChatConstants;
import com.pte.chat.internal.service.quota.ChatQuotaSnapshot;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;

public class ChatQuotaExceededException extends DomainException {

    public ChatQuotaExceededException(ChatQuotaSnapshot snapshot) {
        super(HttpStatus.TOO_MANY_REQUESTS, ChatConstants.ERR_QUOTA_EXCEEDED, data(snapshot),
                ChatConstants.QUOTA_EXCEEDED_MESSAGE);
    }

    private static Map<String, Object> data(ChatQuotaSnapshot snapshot) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(ChatConstants.DATA_RESET_AT, snapshot.resetAt().toString());
        data.put(ChatConstants.DATA_LIMIT, snapshot.limit());
        data.put(ChatConstants.DATA_REMAINING, 0);
        return data;
    }
}
