package com.pte.chat.internal.service;

import com.pte.chat.internal.client.ChatAiException;
import com.pte.chat.internal.client.ChatAiFailureReason;
import com.pte.chat.internal.client.ChatAiUsage;
import com.pte.chat.internal.constant.ChatConstants;
import com.pte.chat.internal.exception.ChatBusyException;
import com.pte.chat.internal.exception.ChatQuotaExceededException;
import com.pte.chat.internal.exception.ChatRequestInFlightException;
import com.pte.chat.internal.service.quota.ChatQuotaSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Every way a charged message can go wrong: the quota comes back exactly once, the failure is reported as a
 * "busy" 503 (or the original domain error), nothing is retried, and the in-flight lock and the slot are released
 * last, on every path.
 */
class ChatServiceFailureTest extends ChatServiceTestSupport {

    private void assertLeasesClosedAfterTheRefund() {
        InOrder order = inOrder(quotaService, slotLease, studentLease);
        order.verify(quotaService).refundQuietly(ticket);
        order.verify(slotLease).close();
        order.verify(studentLease).close();
    }

    private void assertBothLeasesClosedOnce() {
        verify(slotLease, times(1)).close();
        verify(studentLease, times(1)).close();
    }

    // ---- provider failures -------------------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(ChatAiFailureReason.class)
    void everyProviderFailureRefundsCountsAFailedMessageAndAnswersBusyAfterOneCall(ChatAiFailureReason reason) {
        when(client.generate(any(), any(), any())).thenThrow(new ChatAiException(reason, "provider failed"));

        assertThatThrownBy(() -> service.send(caller, command("Question"))).isInstanceOf(ChatBusyException.class);

        verify(client, times(1)).generate(any(), any(), any());
        verify(quotaService, times(1)).refundQuietly(ticket);
        verify(observability).providerFailed(reason);
        verify(observability).quotaRefunded(anyString());
        verify(usageRecorder).record(MODEL, ChatConstants.STATUS_FAILED, ChatAiUsage.NONE);
        assertLeasesClosedAfterTheRefund();
        assertBothLeasesClosedOnce();
    }

    @Test
    void aFailedMessageKeepsTheCostTheProviderReported() {
        ChatAiUsage usage = new ChatAiUsage(11, 800, 5, "MAX_TOKENS");
        when(client.generate(any(), any(), any()))
                .thenThrow(new ChatAiException(ChatAiFailureReason.TRUNCATED, "hit the cap", usage));

        assertThatThrownBy(() -> service.send(caller, command("  Question  ", "WRITE_ESSAY", "vi")))
                .isInstanceOf(ChatBusyException.class);

        verify(usageRecorder).record(MODEL, ChatConstants.STATUS_FAILED, usage);
    }

    @Test
    void aBrokenCounterNeverHidesTheProviderFailure() {
        when(client.generate(any(), any(), any()))
                .thenThrow(new ChatAiException(ChatAiFailureReason.UNAVAILABLE, "503"));

        assertThatThrownBy(() -> service.send(caller, command("Question"))).isInstanceOf(ChatBusyException.class);

        verify(quotaService, times(1)).refundQuietly(ticket);
        assertLeasesClosedAfterTheRefund();
    }

    // ---- unexpected failures of the client ---------------------------------------------------------------

    static Stream<RuntimeException> unexpectedClientFailures() {
        return Stream.of(new IllegalStateException("bug"), new NullPointerException("bug"),
                new ArrayIndexOutOfBoundsException("bug"), new UnsupportedOperationException("bug"));
    }

    @ParameterizedTest
    @MethodSource("unexpectedClientFailures")
    void anyRuntimeExceptionFromTheClientIsTreatedLikeAProviderFailure(RuntimeException failure) {
        when(client.generate(any(), any(), any())).thenThrow(failure);

        assertThatThrownBy(() -> service.send(caller, command("Question"))).isInstanceOf(ChatBusyException.class);

        verify(client, times(1)).generate(any(), any(), any());
        verify(quotaService, times(1)).refundQuietly(ticket);
        verify(observability).quotaRefunded(anyString());
        verify(observability, never()).providerFailed(any());
        verify(usageRecorder).record(MODEL, ChatConstants.STATUS_FAILED, ChatAiUsage.NONE);
        assertLeasesClosedAfterTheRefund();
        assertBothLeasesClosedOnce();
    }

    // ---- a failing refund --------------------------------------------------------------------------------

    @Test
    void aFailingRefundDoesNotMaskAProviderFailure() {
        when(client.generate(any(), any(), any()))
                .thenThrow(new ChatAiException(ChatAiFailureReason.TIMEOUT, "timed out"));
        doThrow(new IllegalStateException("refund boom")).when(quotaService).refundQuietly(ticket);

        assertThatThrownBy(() -> service.send(caller, command("Question"))).isInstanceOf(ChatBusyException.class);

        assertLeasesClosedAfterTheRefund();
        assertBothLeasesClosedOnce();
    }

    @Test
    void aFailingRefundDoesNotMaskAnOffTopicReply() {
        when(client.generate(any(), any(), any())).thenReturn(offTopicAnswer());
        doThrow(new IllegalStateException("refund boom")).when(quotaService).refundQuietly(ticket);

        assertThat(service.send(caller, command("What is 2 + 2?")).onTopic()).isFalse();

        assertBothLeasesClosedOnce();
    }

    // ---- rejections before the charge --------------------------------------------------------------------

    @Test
    void aSecondRequestOfTheSameStudentIsRejectedWithoutTouchingQuotaOrProvider() {
        ChatRequestInFlightException inFlight = new ChatRequestInFlightException();
        when(gate.lockStudent(STUDENT)).thenThrow(inFlight);

        assertThatThrownBy(() -> service.send(caller, command("Question"))).isSameAs(inFlight);

        verify(gate, never()).acquireSlot();
        verifyNoInteractions(quotaService, client, usageRecorder, slotLease, studentLease);
    }

    @Test
    void whenAllSlotsAreTakenTheQuotaIsUntouchedAndTheStudentLockIsReleased() {
        ChatBusyException busy = new ChatBusyException();
        when(gate.acquireSlot()).thenThrow(busy);

        assertThatThrownBy(() -> service.send(caller, command("Question"))).isSameAs(busy);

        verifyNoInteractions(quotaService, client, usageRecorder, slotLease);
        verify(studentLease, times(1)).close();
    }

    @Test
    void anExhaustedQuotaIsReportedAsIsAndBothLeasesAreReleasedWithoutARefundOrACount() {
        ChatQuotaExceededException exhausted = new ChatQuotaExceededException(
                new ChatQuotaSnapshot(20, 0, 20, Instant.parse("2026-10-28T17:00:00Z")));
        when(quotaService.consume(STUDENT)).thenThrow(exhausted);

        assertThatThrownBy(() -> service.send(caller, command("Question"))).isSameAs(exhausted);

        verify(quotaService, never()).refundQuietly(any());
        verifyNoInteractions(client, usageRecorder);
        assertBothLeasesClosedOnce();
        InOrder order = inOrder(slotLease, studentLease);
        order.verify(slotLease).close();
        order.verify(studentLease).close();
    }
}
