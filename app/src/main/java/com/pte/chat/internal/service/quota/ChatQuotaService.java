package com.pte.chat.internal.service.quota;

import com.pte.chat.internal.config.ChatAiProperties;
import com.pte.chat.internal.constant.ChatConstants;
import com.pte.chat.internal.exception.ChatQuotaExceededException;
import com.pte.chat.internal.service.ChatErrors;
import com.pte.chat.internal.service.ChatObservability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Daily question quota. The day is always the server's (Vietnam time), and a charge hands back a ticket that
 * remembers the charged day, so a refund or a quota read after midnight still talks about the day that was
 * charged and never about the new one.
 */
@Service
@ConditionalOnProperty(name = ChatConstants.PROPERTY_ENABLED, havingValue = "true")
public class ChatQuotaService {

    private static final Logger log = LoggerFactory.getLogger(ChatQuotaService.class);

    private final ChatQuotaRepository repository;
    private final ChatAiProperties properties;
    private final Clock vnClock;
    private final ChatObservability observability;

    public ChatQuotaService(ChatQuotaRepository repository, ChatAiProperties properties, Clock clock,
                            ChatObservability observability) {
        this.repository = repository;
        this.properties = properties;
        this.vnClock = clock.withZone(ChatConstants.ZONE);
        this.observability = observability;
    }

    public ChatQuotaTicket consume(UUID studentId) {
        LocalDate day = LocalDate.now(vnClock);
        int limit = properties.getDailyLimit();
        OptionalInt used = repository.consume(studentId, day, limit);
        if (used.isEmpty()) {
            throw new ChatQuotaExceededException(new ChatQuotaSnapshot(limit, 0, limit, resetAt(day)));
        }
        return new ChatQuotaTicket(studentId, day);
    }

    public ChatQuotaSnapshot snapshot(UUID studentId) {
        return snapshot(studentId, LocalDate.now(vnClock));
    }

    public ChatQuotaSnapshot snapshot(ChatQuotaTicket ticket) {
        return snapshot(ticket.studentId(), ticket.usageDate());
    }

    public void refundQuietly(ChatQuotaTicket ticket) {
        try {
            repository.refund(ticket.studentId(), ticket.usageDate());
        } catch (Exception ex) {
            log.error("Chat quota refund failed for day {}: {}", ticket.usageDate(), ChatErrors.describe(ex));
            observability.refundFailed();
        }
    }

    private ChatQuotaSnapshot snapshot(UUID studentId, LocalDate day) {
        int limit = properties.getDailyLimit();
        int used = repository.used(studentId, day);
        return new ChatQuotaSnapshot(used, Math.max(limit - used, 0), limit, resetAt(day));
    }

    private static Instant resetAt(LocalDate day) {
        return day.plusDays(1).atStartOfDay(ChatConstants.ZONE).toInstant();
    }
}
