package com.pte.chat.internal.service;

import com.pte.chat.internal.client.ChatAiException;
import com.pte.chat.internal.client.ChatAiFailureReason;
import com.pte.chat.internal.client.ChatAiUsage;
import com.pte.chat.internal.constant.ChatConstants;
import com.pte.chat.internal.domain.ChatMessage;
import com.pte.chat.internal.exception.ChatBusyException;
import com.pte.chat.internal.exception.ChatQuotaExceededException;
import com.pte.chat.internal.exception.ChatRequestInFlightException;
import com.pte.chat.internal.exception.ChatSessionNotFoundException;
import com.pte.chat.internal.service.quota.ChatQuotaSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
    void everyProviderFailureRefundsStoresAFailedRowAndAnswersBusyAfterOneCall(ChatAiFailureReason reason) {
        when(client.generate(any(), any(), any())).thenThrow(new ChatAiException(reason, "provider failed"));

        assertThatThrownBy(() -> service.send(caller, command("Question"))).isInstanceOf(ChatBusyException.class);

        verify(client, times(1)).generate(any(), any(), any());
        verify(quotaService, times(1)).refundQuietly(ticket);
        verify(observability).providerFailed(reason);
        verify(observability).quotaRefunded(anyString());
        assertThat(savedMessage().getStatus()).isEqualTo(ChatConstants.STATUS_FAILED);
        assertLeasesClosedAfterTheRefund();
        assertBothLeasesClosedOnce();
    }

    @Test
    void aFailedRowKeepsTheCostTheProviderReportedButNoAnswerAndNoVerdict() {
        ChatAiUsage usage = new ChatAiUsage(11, 800, 5, "MAX_TOKENS");
        when(client.generate(any(), any(), any()))
                .thenThrow(new ChatAiException(ChatAiFailureReason.TRUNCATED, "hit the cap", usage));

        assertThatThrownBy(() -> service.send(caller, command("  Question  ", "WRITE_ESSAY", "vi")))
                .isInstanceOf(ChatBusyException.class);

        ChatMessage saved = savedMessage();
        assertThat(saved.getStatus()).isEqualTo(ChatConstants.STATUS_FAILED);
        assertThat(saved.getAnswer()).isEmpty();
        assertThat(saved.getFinishReason()).isEqualTo("MAX_TOKENS");
        assertThat(saved.getTokensIn()).isEqualTo(11);
        assertThat(saved.getTokensOut()).isEqualTo(800);
        assertThat(saved.getTokensThinking()).isEqualTo(5);
        assertThat(saved.getOnTopic()).isNull();
        assertThat(saved.getModel()).isEqualTo(MODEL);
        assertThat(saved.isFromCache()).isFalse();
        assertThat(saved.getQuestion()).isEqualTo("Question");
        assertThat(saved.getSessionId()).isEqualTo(sessionId);
        assertThat(saved.getStudentId()).isEqualTo(STUDENT);
        assertThat(saved.getTenantId()).isEqualTo(TENANT);
        assertThat(saved.getTaskType()).isEqualTo("WRITE_ESSAY");
        assertThat(saved.getLanguage()).isEqualTo("vi");
    }

    @Test
    void aFailureBeforeAnyResponseStoresZeroTokensAndNoFinishReason() {
        when(client.generate(any(), any(), any()))
                .thenThrow(new ChatAiException(ChatAiFailureReason.TIMEOUT, "timed out"));

        assertThatThrownBy(() -> service.send(caller, command("Question"))).isInstanceOf(ChatBusyException.class);

        ChatMessage saved = savedMessage();
        assertThat(saved.getTokensIn()).isZero();
        assertThat(saved.getTokensOut()).isZero();
        assertThat(saved.getTokensThinking()).isZero();
        assertThat(saved.getFinishReason()).isNull();
    }

    @Test
    void aFailedRowDoesNotCacheAndDoesNotCountAsAnAnswer() {
        when(client.generate(any(), any(), any()))
                .thenThrow(new ChatAiException(ChatAiFailureReason.RATE_LIMITED, "429"));

        assertThatThrownBy(() -> service.send(caller, command("Question"))).isInstanceOf(ChatBusyException.class);

        verify(cacheService, never()).storeQuietly(any(), any(), any());
        verify(observability).messageStored(ChatConstants.STATUS_FAILED, false);
    }

    @Test
    void ifTheFailedRowCannotBeSavedTheStudentStillGetsBusyAndTheRefund() {
        when(client.generate(any(), any(), any()))
                .thenThrow(new ChatAiException(ChatAiFailureReason.UNAVAILABLE, "503"));
        when(messageRepository.save(any(ChatMessage.class))).thenThrow(new IllegalStateException("db down"));

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
        ChatMessage saved = savedMessage();
        assertThat(saved.getStatus()).isEqualTo(ChatConstants.STATUS_FAILED);
        assertThat(saved.getAnswer()).isEmpty();
        assertThat(saved.getTokensIn()).isZero();
        assertThat(saved.getTokensOut()).isZero();
        assertThat(saved.getTokensThinking()).isZero();
        assertThat(saved.getFinishReason()).isNull();
        assertThat(saved.getOnTopic()).isNull();
        assertThat(saved.getModel()).isEqualTo(MODEL);
        assertLeasesClosedAfterTheRefund();
        assertBothLeasesClosedOnce();
    }

    // ---- storing the answer fails ------------------------------------------------------------------------

    @Test
    void ifTheGoodAnswerCannotBeStoredTheQuotaIsRefundedAndTheStudentGetsBusy() {
        when(messageRepository.save(any(ChatMessage.class))).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> service.send(caller, command("Question"))).isInstanceOf(ChatBusyException.class);

        verify(client, times(1)).generate(any(), any(), any());
        verify(quotaService, times(1)).refundQuietly(ticket);
        verify(cacheService, never()).storeQuietly(any(), any(), any());
        verify(observability, never()).messageStored(ChatConstants.STATUS_OK, false);
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

    @Test
    void aFailingRefundDoesNotMaskAStoreFailure() {
        when(messageRepository.save(any(ChatMessage.class))).thenThrow(new IllegalStateException("db down"));
        doThrow(new IllegalStateException("refund boom")).when(quotaService).refundQuietly(ticket);

        assertThatThrownBy(() -> service.send(caller, command("Question"))).isInstanceOf(ChatBusyException.class);

        assertBothLeasesClosedOnce();
    }

    // ---- ownership lost after the charge -----------------------------------------------------------------

    @Test
    void ifSomeoneElseOwnsTheSessionAfterTheChargeTheQuotaIsRefundedAndNothingIsSent() {
        ChatSessionNotFoundException notFound = new ChatSessionNotFoundException();
        doThrow(notFound).when(sessionService).claim(sessionId, STUDENT, NOW);

        assertThatThrownBy(() -> service.send(caller, command("Question"))).isSameAs(notFound);

        verify(quotaService, times(1)).refundQuietly(ticket);
        verifyNoInteractions(client);
        verify(messageRepository, never()).save(any(ChatMessage.class));
        verify(cacheService, never()).storeQuietly(any(), any(), any());
        assertLeasesClosedAfterTheRefund();
        assertBothLeasesClosedOnce();
    }

    @Test
    void ifLoadingTheHistoryFailsAfterTheChargeTheQuotaIsRefundedAndNothingIsSent() {
        when(sessionService.history(any(), any(), anyInt())).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> service.send(caller, command("Question"))).isInstanceOf(RuntimeException.class);

        verify(quotaService, times(1)).refundQuietly(ticket);
        verifyNoInteractions(client);
        assertLeasesClosedAfterTheRefund();
        assertBothLeasesClosedOnce();
    }

    // ---- rejections before the charge --------------------------------------------------------------------

    @Test
    void anotherStudentsSessionIsRejectedBeforeAnyLockIsTaken() {
        ChatSessionNotFoundException notFound = new ChatSessionNotFoundException();
        doThrow(notFound).when(sessionService).requireNotOwnedByOther(sessionId, STUDENT);

        assertThatThrownBy(() -> service.send(caller, command("Question"))).isSameAs(notFound);

        verifyNoInteractions(gate, quotaService, client, cacheService, messageRepository);
    }

    @Test
    void aSecondRequestOfTheSameStudentIsRejectedWithoutTouchingQuotaCacheOrProvider() {
        ChatRequestInFlightException inFlight = new ChatRequestInFlightException();
        when(gate.lockStudent(STUDENT)).thenThrow(inFlight);

        assertThatThrownBy(() -> service.send(caller, command("Question"))).isSameAs(inFlight);

        verify(gate, never()).acquireSlot();
        verifyNoInteractions(quotaService, client, cacheService, messageRepository);
        verifyNoInteractions(slotLease, studentLease);
    }

    @Test
    void whenAllSlotsAreTakenTheQuotaIsUntouchedAndTheStudentLockIsReleased() {
        ChatBusyException busy = new ChatBusyException();
        when(gate.acquireSlot()).thenThrow(busy);

        assertThatThrownBy(() -> service.send(caller, command("Question"))).isSameAs(busy);

        verifyNoInteractions(quotaService, client, messageRepository);
        verify(cacheService, never()).storeQuietly(any(), any(), any());
        verify(studentLease, times(1)).close();
        verifyNoInteractions(slotLease);
    }

    @Test
    void anExhaustedQuotaIsReportedAsIsAndBothLeasesAreReleasedWithoutARefund() {
        ChatQuotaExceededException exhausted = new ChatQuotaExceededException(
                new ChatQuotaSnapshot(20, 0, 20, Instant.parse("2026-10-28T17:00:00Z")));
        when(quotaService.consume(STUDENT)).thenThrow(exhausted);

        assertThatThrownBy(() -> service.send(caller, command("Question"))).isSameAs(exhausted);

        verify(quotaService, never()).refundQuietly(any());
        verify(sessionService, never()).claim(any(), any(), any());
        verifyNoInteractions(client, messageRepository);
        assertBothLeasesClosedOnce();
        InOrder order = inOrder(slotLease, studentLease);
        order.verify(slotLease).close();
        order.verify(studentLease).close();
    }

    @Test
    void anExhaustedQuotaStoresNoSessionAndNoMessage() {
        when(quotaService.consume(STUDENT)).thenThrow(new ChatQuotaExceededException(
                new ChatQuotaSnapshot(20, 0, 20, Instant.parse("2026-10-28T17:00:00Z"))));

        assertThatThrownBy(() -> service.send(caller, command("Question")))
                .isInstanceOf(ChatQuotaExceededException.class);

        verify(sessionService, never()).claim(any(), any(), any());
        verifyNoInteractions(messageRepository);
    }
}
