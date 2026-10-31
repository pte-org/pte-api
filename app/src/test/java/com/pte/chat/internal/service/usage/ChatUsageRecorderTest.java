package com.pte.chat.internal.service.usage;

import com.pte.chat.internal.client.ChatAiFailureReason;
import com.pte.chat.internal.client.ChatAiUsage;
import com.pte.chat.internal.config.ChatAiProperties;
import com.pte.chat.internal.constant.ChatConstants;
import com.pte.chat.internal.service.ChatObservability;
import com.pte.chat.support.MutableClock;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ChatUsageRecorderTest {

    private final ChatUsageDailyRepository repository = mock(ChatUsageDailyRepository.class);
    private final ChatObservability observability = mock(ChatObservability.class);
    private final ChatAiProperties properties = new ChatAiProperties();
    // 2026-10-28 23:30 UTC is 06:30 on the 29th in Vietnam.
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-28T23:30:00Z"));
    private final ChatUsageRecorder recorder = new ChatUsageRecorder(repository, properties, clock, observability);

    @Test
    void countsTheMessageOnTheVietnamDayWithTheCostOfItsTokens() {
        properties.setPriceInputPerMillionUsd(new BigDecimal("0.10"));
        properties.setPriceOutputPerMillionUsd(new BigDecimal("0.40"));

        recorder.record("gemini-3.5-flash-lite", ChatConstants.STATUS_OK, new ChatAiUsage(1_000_000, 500_000, 500_000, "STOP"));

        // 1M in * 0.10 + (0.5M out + 0.5M thinking) * 0.40
        verify(repository).add(eq(LocalDate.of(2026, 10, 29)), eq("gemini-3.5-flash-lite"), eq(ChatConstants.STATUS_OK),
                eq(ChatFailureBucket.OTHER), any(ChatAiUsage.class), eq(new BigDecimal("0.500000")));
    }

    @Test
    void aFailedCallWithNoTokensCostsNothing() {
        recorder.record("m", ChatConstants.STATUS_FAILED, ChatAiUsage.NONE);

        verify(repository).add(any(), eq("m"), eq(ChatConstants.STATUS_FAILED), eq(ChatFailureBucket.OTHER),
                eq(ChatAiUsage.NONE), eq(new BigDecimal("0.000000")));
    }

    @Test
    void aFailedCallIsCountedUnderTheGroupOfItsReason() {
        recorder.recordFailure("m", ChatAiUsage.NONE, ChatAiFailureReason.TIMEOUT);
        recorder.recordFailure("m", ChatAiUsage.NONE, ChatAiFailureReason.RATE_LIMITED);
        recorder.recordFailure("m", ChatAiUsage.NONE, null);

        verify(repository).add(any(), eq("m"), eq(ChatConstants.STATUS_FAILED), eq(ChatFailureBucket.TIMEOUT),
                any(ChatAiUsage.class), any());
        verify(repository).add(any(), eq("m"), eq(ChatConstants.STATUS_FAILED), eq(ChatFailureBucket.RATE_LIMITED),
                any(ChatAiUsage.class), any());
        verify(repository).add(any(), eq("m"), eq(ChatConstants.STATUS_FAILED), eq(ChatFailureBucket.OTHER),
                any(ChatAiUsage.class), any());
    }

    @Test
    void aBrokenCounterNeverStopsTheAnswer() {
        doThrow(new IllegalStateException("db down")).when(repository).add(any(), any(), any(), any(), any(), any());

        assertThatCode(() -> recorder.record("m", ChatConstants.STATUS_OK, ChatAiUsage.NONE)).doesNotThrowAnyException();
        verify(observability).usageRecordFailed();
    }
}
