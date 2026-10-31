package com.pte.chat.internal.service.usage;

import com.pte.chat.internal.client.ChatAiUsage;
import com.pte.chat.internal.config.ChatAiProperties;
import com.pte.chat.internal.constant.ChatConstants;
import com.pte.chat.internal.service.ChatErrors;
import com.pte.chat.internal.service.ChatObservability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;

/**
 * Counts each finished message for the admin cost screen. The estimate is the tokens times the configured
 * prices at the moment of the message, so a later price or model change never rewrites an earlier day. Counting
 * is best effort: a broken counter is logged and never changes what the student gets.
 */
@Service
@ConditionalOnProperty(name = ChatConstants.PROPERTY_ENABLED, havingValue = "true")
public class ChatUsageRecorder {

    private static final Logger log = LoggerFactory.getLogger(ChatUsageRecorder.class);
    private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000);
    private static final int COST_SCALE = 6;

    private final ChatUsageDailyRepository repository;
    private final ChatAiProperties properties;
    private final Clock clock;
    private final ChatObservability observability;

    public ChatUsageRecorder(ChatUsageDailyRepository repository, ChatAiProperties properties, Clock clock,
                             ChatObservability observability) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
        this.observability = observability;
    }

    public void record(String model, String status, ChatAiUsage usage) {
        try {
            LocalDate day = LocalDate.now(clock.withZone(ChatConstants.ZONE));
            repository.add(day, model, status, usage, cost(usage));
            observability.messageCounted(status);
        } catch (RuntimeException ex) {
            log.error("Could not count a chat message: {}", ChatErrors.describe(ex));
            observability.usageRecordFailed();
        }
    }

    /** Thinking tokens are billed as output. */
    private BigDecimal cost(ChatAiUsage usage) {
        BigDecimal in = properties.getPriceInputPerMillionUsd().multiply(BigDecimal.valueOf(usage.tokensIn()));
        BigDecimal out = properties.getPriceOutputPerMillionUsd()
                .multiply(BigDecimal.valueOf((long) usage.tokensOut() + usage.tokensThinking()));
        return in.add(out).divide(ONE_MILLION, COST_SCALE, RoundingMode.HALF_UP);
    }
}
