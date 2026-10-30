package com.pte.chat.internal.service;

import com.pte.chat.internal.service.quota.ChatQuotaSnapshot;

import java.util.UUID;

public record ChatReply(String markdown, boolean onTopic, boolean fromCache, ChatQuotaSnapshot quota, UUID sessionId) {
}
