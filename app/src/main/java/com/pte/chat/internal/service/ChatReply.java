package com.pte.chat.internal.service;

import com.pte.chat.internal.service.quota.ChatQuotaSnapshot;

public record ChatReply(String markdown, boolean onTopic, ChatQuotaSnapshot quota) {
}
