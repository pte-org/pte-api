package com.pte.chat.internal.service;

import com.pte.chat.internal.client.ChatAiAnswer;
import com.pte.chat.internal.client.ChatAiException;
import com.pte.chat.internal.client.ChatAiUsage;
import com.pte.chat.internal.client.ChatTurn;
import com.pte.chat.internal.client.GeminiChatClient;
import com.pte.chat.internal.config.ChatAiProperties;
import com.pte.chat.internal.constant.ChatConstants;
import com.pte.chat.internal.domain.ChatMessage;
import com.pte.chat.internal.exception.ChatBusyException;
import com.pte.chat.internal.prompt.ChatPrompts;
import com.pte.chat.internal.repository.ChatMessageRepository;
import com.pte.chat.internal.service.cache.ChatAnswerCacheService;
import com.pte.chat.internal.service.cache.ChatCacheKey;
import com.pte.chat.internal.service.quota.ChatQuotaService;
import com.pte.chat.internal.service.quota.ChatQuotaSnapshot;
import com.pte.chat.internal.service.quota.ChatQuotaTicket;
import com.pte.chat.internal.service.session.ChatConcurrencyGate;
import com.pte.chat.internal.service.session.ChatConcurrencyGate.Lease;
import com.pte.chat.internal.service.session.ChatSessionService;
import com.pte.practice.PracticeEntitlementGate;
import com.pte.shared.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * One student question, end to end: entitlement, validation, session owner check, one request per student,
 * cache for a fresh session, a global slot, the quota charge, the Gemini call and the stored result.
 *
 * <p>Order matters. Nothing is written before the charge except what a cache hit needs, and every path after
 * the charge ends in either a stored OK answer or a refund. The refund is attempted before the student lock
 * and the slot are released, and a failing refund never hides the original outcome. A failed call is stored
 * as a FAILED row (empty answer, finish reason and tokens kept) so provider cost stays visible after the
 * refund. The provider call is made once: there is no retry anywhere.
 *
 * <p>Locks and the slot live in memory, so the per-student and global limits hold for a single backend
 * instance only; the quota itself is in the database and is correct for any number of instances.
 */
@Service
@ConditionalOnProperty(name = ChatConstants.PROPERTY_ENABLED, havingValue = "true")
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final String CAUSE_OFF_TOPIC = "off_topic";
    private static final String CAUSE_PROVIDER = "provider_failure";
    private static final String CAUSE_STORE = "store_failure";
    private static final String CAUSE_SETUP = "setup_failure";

    private final PracticeEntitlementGate entitlementGate;
    private final ChatAiProperties properties;
    private final ChatQuotaService quotaService;
    private final ChatSessionService sessionService;
    private final ChatConcurrencyGate gate;
    private final ChatAnswerCacheService cacheService;
    private final GeminiChatClient client;
    private final ChatMessageRepository messageRepository;
    private final ChatObservability observability;
    private final Clock clock;
    private final ChatSendValidator validator;
    private final ChatMessageFactory messages;

    public ChatService(PracticeEntitlementGate entitlementGate, ChatAiProperties properties,
                       ChatQuotaService quotaService, ChatSessionService sessionService, ChatConcurrencyGate gate,
                       ChatAnswerCacheService cacheService, GeminiChatClient client,
                       ChatMessageRepository messageRepository, ChatObservability observability, Clock clock) {
        this.entitlementGate = entitlementGate;
        this.properties = properties;
        this.quotaService = quotaService;
        this.sessionService = sessionService;
        this.gate = gate;
        this.cacheService = cacheService;
        this.client = client;
        this.messageRepository = messageRepository;
        this.observability = observability;
        this.clock = clock;
        this.validator = new ChatSendValidator(properties);
        this.messages = new ChatMessageFactory(properties);
    }

    public ChatQuotaSnapshot quota(CurrentUser caller) {
        entitlementGate.assertUnlocked(caller, null);
        return quotaService.snapshot(caller.userId());
    }

    public ChatLatestSession latestSession(CurrentUser caller) {
        entitlementGate.assertUnlocked(caller, null);
        Optional<UUID> latest = sessionService.latestSessionId(caller.userId());
        if (latest.isEmpty()) {
            return new ChatLatestSession(null, List.of());
        }
        return new ChatLatestSession(latest.get(), sessionService.displayMessages(latest.get(), caller.userId(),
                ChatConstants.DISPLAY_MESSAGE_LIMIT));
    }

    public ChatReply send(CurrentUser caller, ChatSendCommand command) {
        entitlementGate.assertUnlocked(caller, null);
        ChatSendInput input = validator.validate(command);
        UUID studentId = caller.userId();
        sessionService.requireNotOwnedByOther(input.sessionId(), studentId);
        Lease studentLease = gate.lockStudent(studentId);
        try {
            boolean emptySession = !sessionService.hasAnswers(input.sessionId(), studentId);
            String cacheKey = ChatCacheKey.of(input.message(), input.language(), input.taskType(), ChatPrompts.VERSION);
            if (emptySession && cacheHitsAllowed(studentId)) {
                Optional<String> hit = cacheService.find(cacheKey);
                if (hit.isPresent()) {
                    return cacheHit(caller, input, hit.get());
                }
            }
            Lease slot = gate.acquireSlot();
            try {
                return charged(caller, input, emptySession, cacheKey);
            } finally {
                slot.close();
            }
        } finally {
            studentLease.close();
        }
    }

    /**
     * A cache hit costs the student nothing but still writes a session and a message, so each student has a
     * daily cap on them. Past the cap the question simply goes the normal, charged way: abuse then spends the
     * student's own quota instead of growing the tables for free.
     */
    private boolean cacheHitsAllowed(UUID studentId) {
        Instant dayStart = clock.instant().atZone(ChatConstants.ZONE).toLocalDate()
                .atStartOfDay(ChatConstants.ZONE).toInstant();
        return sessionService.cacheHitsSince(studentId, dayStart) < properties.getMaxCacheHitsPerDay();
    }

    private ChatReply cacheHit(CurrentUser caller, ChatSendInput input, String answer) {
        sessionService.claim(input.sessionId(), caller.userId(), clock.instant());
        try {
            messageRepository.save(messages.cached(caller, input, answer));
        } catch (RuntimeException ex) {
            log.error("Could not store a cached chat answer: {}", ChatErrors.describe(ex));
            throw new ChatBusyException();
        }
        observability.messageStored(ChatConstants.STATUS_OK, true);
        return new ChatReply(answer, true, true, quotaService.snapshot(caller.userId()), input.sessionId());
    }

    private ChatReply charged(CurrentUser caller, ChatSendInput input, boolean emptySession, String cacheKey) {
        UUID studentId = caller.userId();
        ChatQuotaTicket ticket = quotaService.consume(studentId);
        List<ChatTurn> turns;
        try {
            sessionService.claim(input.sessionId(), studentId, clock.instant());
            turns = turns(sessionService.history(input.sessionId(), studentId, properties.getHistorySize()));
        } catch (RuntimeException ex) {
            refund(ticket, CAUSE_SETUP);
            throw ex;
        }
        ChatAiAnswer answer;
        try {
            answer = client.generate(ChatPrompts.systemInstruction(), turns, input.message());
        } catch (ChatAiException ex) {
            observability.providerFailed(ex.reason());
            return failed(caller, input, ticket, ex.usage());
        } catch (RuntimeException ex) {
            log.error("Chat provider call failed unexpectedly: {}", ChatErrors.describe(ex));
            return failed(caller, input, ticket, ChatAiUsage.NONE);
        }
        if (!answer.onTopic()) {
            refund(ticket, CAUSE_OFF_TOPIC);
            return refused(caller, input, ticket, answer);
        }
        try {
            messageRepository.save(messages.answered(caller, input, answer));
        } catch (RuntimeException ex) {
            log.error("Could not store a chat answer: {}", ChatErrors.describe(ex));
            refund(ticket, CAUSE_STORE);
            throw new ChatBusyException();
        }
        observability.messageStored(ChatConstants.STATUS_OK, false);
        if (emptySession) {
            storeInCache(cacheKey, answer);
        }
        return new ChatReply(answer.markdown(), true, false, quotaService.snapshot(ticket), input.sessionId());
    }

    private void storeInCache(String cacheKey, ChatAiAnswer answer) {
        try {
            cacheService.storeQuietly(cacheKey, answer.markdown(), answer.model());
        } catch (RuntimeException ex) {
            log.warn("Chat answer cache write failed: {}", ChatErrors.describe(ex));
        }
    }

    private ChatReply refused(CurrentUser caller, ChatSendInput input, ChatQuotaTicket ticket, ChatAiAnswer answer) {
        String refusal = ChatPrompts.refusal(input.language());
        saveBestEffort(messages.refused(caller, input, answer, refusal));
        return new ChatReply(refusal, false, false, quotaService.snapshot(ticket), input.sessionId());
    }

    private ChatReply failed(CurrentUser caller, ChatSendInput input, ChatQuotaTicket ticket, ChatAiUsage usage) {
        refund(ticket, CAUSE_PROVIDER);
        saveBestEffort(messages.failed(caller, input, usage));
        throw new ChatBusyException();
    }

    private void saveBestEffort(ChatMessage message) {
        try {
            messageRepository.save(message);
            observability.messageStored(message.getStatus(), false);
        } catch (RuntimeException ex) {
            log.error("Could not store a {} chat message: {}", message.getStatus(), ChatErrors.describe(ex));
        }
    }

    /** Refunds the charged day. It never throws, so it cannot hide the failure that made the refund necessary. */
    private void refund(ChatQuotaTicket ticket, String cause) {
        try {
            quotaService.refundQuietly(ticket);
            observability.quotaRefunded(cause);
        } catch (RuntimeException ex) {
            log.error("Chat quota refund threw: {}", ChatErrors.describe(ex));
        }
    }

    private static List<ChatTurn> turns(List<ChatMessage> history) {
        List<ChatTurn> turns = new ArrayList<>();
        for (ChatMessage message : history) {
            turns.add(ChatTurn.user(message.getQuestion()));
            turns.add(ChatTurn.model(message.getAnswer()));
        }
        return turns;
    }
}
