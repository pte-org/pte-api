package com.pte.chat.internal.service;

import com.pte.chat.internal.client.ChatAiAnswer;
import com.pte.chat.internal.client.ChatAiUsage;
import com.pte.chat.internal.client.GeminiChatClient;
import com.pte.chat.internal.config.ChatAiProperties;
import com.pte.chat.internal.domain.ChatMessage;
import com.pte.chat.internal.repository.ChatMessageRepository;
import com.pte.chat.internal.service.cache.ChatAnswerCacheService;
import com.pte.chat.internal.service.quota.ChatQuotaService;
import com.pte.chat.internal.service.quota.ChatQuotaSnapshot;
import com.pte.chat.internal.service.quota.ChatQuotaTicket;
import com.pte.chat.internal.service.session.ChatConcurrencyGate;
import com.pte.chat.internal.service.session.ChatConcurrencyGate.Lease;
import com.pte.chat.internal.service.session.ChatSessionService;
import com.pte.chat.support.MutableClock;
import com.pte.practice.PracticeEntitlementGate;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
    final UUID sessionId = UUID.randomUUID();

    final PracticeEntitlementGate entitlementGate = mock(PracticeEntitlementGate.class);
    final ChatQuotaService quotaService = mock(ChatQuotaService.class);
    final ChatSessionService sessionService = mock(ChatSessionService.class);
    final ChatConcurrencyGate gate = mock(ChatConcurrencyGate.class);
    final ChatAnswerCacheService cacheService = mock(ChatAnswerCacheService.class);
    final GeminiChatClient client = mock(GeminiChatClient.class);
    final ChatMessageRepository messageRepository = mock(ChatMessageRepository.class);
    final ChatObservability observability = mock(ChatObservability.class);
    final Lease studentLease = mock(Lease.class);
    final Lease slotLease = mock(Lease.class);

    final ChatAiProperties properties = new ChatAiProperties();
    final MutableClock clock = new MutableClock(NOW);
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
        when(sessionService.hasAnswers(any(), any())).thenReturn(false);
        when(sessionService.history(any(), any(), anyInt())).thenReturn(List.of());
        when(cacheService.find(any())).thenReturn(Optional.empty());
        when(client.generate(any(), any(), any())).thenReturn(onTopicAnswer("**Use** has/have + V3."));
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        service = newService();
    }

    ChatService newService() {
        return new ChatService(entitlementGate, properties, quotaService, sessionService, gate, cacheService, client,
                messageRepository, observability, clock);
    }

    ChatSendCommand command(String message) {
        return new ChatSendCommand(sessionId, message, null, "en");
    }

    ChatSendCommand command(String message, String taskType, String language) {
        return new ChatSendCommand(sessionId, message, taskType, language);
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

    ChatMessage message(String question, String answer) {
        ChatMessage message = new ChatMessage();
        message.setQuestion(question);
        message.setAnswer(answer);
        message.setStatus("OK");
        return message;
    }

    /** Every message the service handed to the repository, in order. */
    List<ChatMessage> savedMessages() {
        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageRepository, atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    /** The one message a normal path stores. */
    ChatMessage savedMessage() {
        List<ChatMessage> saved = savedMessages();
        org.assertj.core.api.Assertions.assertThat(saved).hasSize(1);
        return saved.getFirst();
    }
}
