package com.pte.chat.internal.service;

import com.pte.chat.internal.client.ChatAiAnswer;
import com.pte.chat.internal.client.ChatAiUsage;
import com.pte.chat.internal.client.GeminiChatClient;
import com.pte.chat.internal.config.ChatAiProperties;
import com.pte.chat.internal.service.quota.ChatQuotaService;
import com.pte.chat.internal.service.quota.ChatQuotaSnapshot;
import com.pte.chat.internal.service.quota.ChatQuotaTicket;
import com.pte.chat.internal.service.session.ChatConcurrencyGate;
import com.pte.chat.internal.service.session.ChatConcurrencyGate.Lease;
import com.pte.chat.internal.service.usage.ChatUsageRecorder;
import com.pte.practice.PracticeEntitlementGate;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Wiring shared by the pure unit tests of {@link ChatService}: every collaborator is a Mockito mock with a happy
 * default, so each test only overrides the one thing it is about. No Docker, no network, no real clock.
 */
abstract class ChatServiceTestSupport {

    static final UUID STUDENT = UUID.randomUUID();
    static final UUID TENANT = UUID.randomUUID();
    static final String MODEL = "gemini-2.5-flash-lite";
    static final String SERVED_MODEL = "gemini-2.5-flash-lite-001";
    static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");
    static final LocalDate DAY = LocalDate.of(2026, 10, 28);

    final CurrentUser caller = new CurrentUser(STUDENT, TENANT, List.of("STUDENT"));

    final PracticeEntitlementGate entitlementGate = mock(PracticeEntitlementGate.class);
    final ChatQuotaService quotaService = mock(ChatQuotaService.class);
    final ChatConcurrencyGate gate = mock(ChatConcurrencyGate.class);
    final GeminiChatClient client = mock(GeminiChatClient.class);
    final ChatUsageRecorder usageRecorder = mock(ChatUsageRecorder.class);
    final ChatObservability observability = mock(ChatObservability.class);
    final Lease studentLease = mock(Lease.class);
    final Lease slotLease = mock(Lease.class);

    final ChatAiProperties properties = new ChatAiProperties();
    final ChatQuotaTicket ticket = new ChatQuotaTicket(STUDENT, DAY);
    /** What the quota service reports for the charged day after the call. */
    final ChatQuotaSnapshot chargedSnapshot =
            new ChatQuotaSnapshot(1, 19, 20, Instant.parse("2026-10-28T17:00:00Z"));
    /** What the quota service reports for today when nothing was charged. */
    final ChatQuotaSnapshot todaySnapshot =
            new ChatQuotaSnapshot(4, 16, 20, Instant.parse("2026-10-28T17:00:00Z"));

    ChatService service;

    @BeforeEach
    void wireHappyPath() {
        properties.setModel(MODEL);
        properties.setMaxInputChars(1000);
        properties.setHistorySize(6);
        when(gate.lockStudent(STUDENT)).thenReturn(studentLease);
        when(gate.acquireSlot()).thenReturn(slotLease);
        when(quotaService.consume(STUDENT)).thenReturn(ticket);
        when(quotaService.snapshot(ticket)).thenReturn(chargedSnapshot);
        when(quotaService.snapshot(STUDENT)).thenReturn(todaySnapshot);
        when(client.generate(any(), any(), any())).thenReturn(onTopicAnswer("**Use** has/have + V3."));
        service = newService();
    }

    ChatService newService() {
        return new ChatService(entitlementGate, properties, quotaService, gate, client, usageRecorder, observability);
    }

    ChatSendCommand command(String message) {
        return new ChatSendCommand(message, null, "en");
    }

    ChatSendCommand command(String message, String taskType, String language) {
        return new ChatSendCommand(message, taskType, language);
    }

    static ChatAiUsage usage() {
        return new ChatAiUsage(11, 22, 3, "STOP");
    }

    static ChatAiAnswer onTopicAnswer(String markdown) {
        return new ChatAiAnswer(true, markdown, usage(), SERVED_MODEL);
    }

    static ChatAiAnswer offTopicAnswer() {
        return new ChatAiAnswer(false, "", usage(), SERVED_MODEL);
    }
}
