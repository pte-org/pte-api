package com.pte.chat.internal.service;

import com.pte.chat.internal.client.ChatAiAnswer;
import com.pte.chat.internal.client.ChatAiException;
import com.pte.chat.internal.client.ChatAiFailureReason;
import com.pte.chat.internal.client.ChatAiUsage;
import com.pte.chat.internal.client.GeminiChatClient;
import com.pte.chat.internal.config.ChatAiProperties;
import com.pte.chat.internal.constant.ChatConstants;
import com.pte.chat.internal.exception.ChatBusyException;
import com.pte.chat.internal.prompt.ChatPrompts;
import com.pte.chat.internal.service.quota.ChatQuotaService;
import com.pte.chat.internal.service.quota.ChatQuotaSnapshot;
import com.pte.chat.internal.service.quota.ChatQuotaTicket;
import com.pte.chat.internal.service.session.ChatConcurrencyGate;
import com.pte.chat.internal.service.session.ChatConcurrencyGate.Lease;
import com.pte.chat.internal.service.usage.ChatUsageRecorder;
import com.pte.practice.PracticeEntitlementGate;
import com.pte.shared.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * One student question, end to end: entitlement, validation, one request per student, a global slot, the quota
 * charge and the Gemini call. Nothing the student typed or was answered is stored: the earlier turns arrive with
 * the request, and the only thing kept afterwards is a count and the tokens (see {@link ChatUsageRecorder}).
 *
 * <p>Every path after the charge ends in either an answer or a refund. The refund is attempted before the
 * student lock and the slot are released, and a failing refund never hides the original outcome. The provider
 * call is made once: there is no retry anywhere.
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

    private final PracticeEntitlementGate entitlementGate;
    private final ChatAiProperties properties;
    private final ChatQuotaService quotaService;
    private final ChatConcurrencyGate gate;
    private final GeminiChatClient client;
    private final ChatUsageRecorder usageRecorder;
    private final ChatObservability observability;
    private final ChatSendValidator validator;

    public ChatService(PracticeEntitlementGate entitlementGate, ChatAiProperties properties,
                       ChatQuotaService quotaService, ChatConcurrencyGate gate, GeminiChatClient client,
                       ChatUsageRecorder usageRecorder, ChatObservability observability) {
        this.entitlementGate = entitlementGate;
        this.properties = properties;
        this.quotaService = quotaService;
        this.gate = gate;
        this.client = client;
        this.usageRecorder = usageRecorder;
        this.observability = observability;
        this.validator = new ChatSendValidator(properties);
    }

    public ChatQuotaSnapshot quota(CurrentUser caller) {
        entitlementGate.assertUnlocked(caller, null);
        return quotaService.snapshot(caller.userId());
    }

    public ChatReply send(CurrentUser caller, ChatSendCommand command) {
        entitlementGate.assertUnlocked(caller, null);
        UUID studentId = caller.userId();
        ChatSendInput input = validator.validate(command);
        Lease studentLease = gate.lockStudent(studentId);
        try {
            Lease slot = gate.acquireSlot();
            try {
                return charged(studentId, input);
            } finally {
                slot.close();
            }
        } finally {
            studentLease.close();
        }
    }

    private ChatReply charged(UUID studentId, ChatSendInput input) {
        ChatQuotaTicket ticket = quotaService.consume(studentId);
        ChatAiAnswer answer;
        try {
            answer = client.generate(ChatPrompts.systemInstruction(), input.history(),
                    ChatPrompts.userTurn(input.message(), input.exercisePrompt(), input.taskType()));
        } catch (ChatAiException ex) {
            // The reason and status come from fixed texts, never from the question, the answer or the key.
            log.warn("Chat provider call failed: reason={} detail={}", ex.reason(), ex.getMessage());
            observability.providerFailed(ex.reason());
            return failed(ticket, ex.usage(), ex.reason());
        } catch (RuntimeException ex) {
            log.error("Chat provider call failed unexpectedly: {}", ChatErrors.describe(ex));
            return failed(ticket, ChatAiUsage.NONE, null);
        }
        if (!answer.onTopic()) {
            refund(ticket, CAUSE_OFF_TOPIC);
            usageRecorder.record(answer.model(), ChatConstants.STATUS_REFUSED, answer.usage());
            return new ChatReply(ChatPrompts.refusal(input.language(), input.message()), false, quotaService.snapshot(ticket));
        }
        usageRecorder.record(answer.model(), ChatConstants.STATUS_OK, answer.usage());
        return new ChatReply(answer.markdown(), true, quotaService.snapshot(ticket));
    }

    private ChatReply failed(ChatQuotaTicket ticket, ChatAiUsage usage, ChatAiFailureReason reason) {
        refund(ticket, CAUSE_PROVIDER);
        usageRecorder.recordFailure(properties.getModel(), usage, reason);
        throw new ChatBusyException();
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
}
