package com.pte.chat.internal.service;

import com.pte.chat.internal.constant.ChatConstants;
import com.pte.chat.internal.domain.ChatMessage;
import com.pte.chat.internal.exception.ChatBusyException;
import com.pte.chat.internal.prompt.ChatPrompts;
import com.pte.chat.internal.service.cache.ChatCacheKey;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The cache path (plan AD-08): consulted only for a session with no answer yet, a hit is free (no quota, no slot,
 * no provider call) and a stored answer is written only after the OK message is safely stored.
 */
class ChatServiceCacheTest extends ChatServiceTestSupport {

    private static final String QUESTION = "How do I use the present perfect?";

    private String key() {
        return ChatCacheKey.of(QUESTION, "en", null, ChatPrompts.VERSION);
    }

    private void cached(String answer) {
        when(cacheService.find(key())).thenReturn(Optional.of(answer));
    }

    // ---- a hit --------------------------------------------------------------------------------------------

    @Test
    void aHitIsReturnedAsACachedOnTopicAnswerWithTodaysQuotaUntouched() {
        cached("cached **answer**");

        ChatReply reply = service.send(caller, command("  " + QUESTION + "  "));

        assertThat(reply.markdown()).isEqualTo("cached **answer**");
        assertThat(reply.fromCache()).isTrue();
        assertThat(reply.onTopic()).isTrue();
        assertThat(reply.sessionId()).isEqualTo(sessionId);
        assertThat(reply.quota()).isSameAs(todaySnapshot);
    }

    @Test
    void aHitDoesNotChargeRefundOrTakeASlotAndNeverCallsTheProvider() {
        cached("cached");

        service.send(caller, command(QUESTION));

        verify(quotaService, never()).consume(any());
        verify(quotaService, never()).refundQuietly(any());
        verify(quotaService, never()).snapshot(ticket);
        verify(gate, never()).acquireSlot();
        verifyNoInteractions(client, slotLease);
    }

    @Test
    void aHitStoresAnOkMessageMarkedFromCacheWithZeroTokens() {
        when(cacheService.find(ChatCacheKey.of(QUESTION, "en", "WRITE_ESSAY", ChatPrompts.VERSION)))
                .thenReturn(Optional.of("cached"));

        service.send(caller, command("  " + QUESTION + "  ", "WRITE_ESSAY", null));

        ChatMessage saved = savedMessage();
        assertThat(saved.getStatus()).isEqualTo(ChatConstants.STATUS_OK);
        assertThat(saved.isFromCache()).isTrue();
        assertThat(saved.getOnTopic()).isTrue();
        assertThat(saved.getAnswer()).isEqualTo("cached");
        assertThat(saved.getQuestion()).isEqualTo(QUESTION);
        assertThat(saved.getTokensIn()).isZero();
        assertThat(saved.getTokensOut()).isZero();
        assertThat(saved.getTokensThinking()).isZero();
        assertThat(saved.getSessionId()).isEqualTo(sessionId);
        assertThat(saved.getStudentId()).isEqualTo(STUDENT);
        assertThat(saved.getTenantId()).isEqualTo(TENANT);
        assertThat(saved.getTaskType()).isEqualTo("WRITE_ESSAY");
        assertThat(saved.getLanguage()).isEqualTo("en");
        assertThat(saved.getPromptVersion()).isEqualTo(ChatPrompts.VERSION);
        verify(observability).messageStored(ChatConstants.STATUS_OK, true);
    }

    @Test
    void aHitClaimsTheSessionBeforeStoringTheMessageAndReleasesTheStudentLock() {
        cached("cached");
        InOrder order = inOrder(entitlementGate, sessionService, gate, cacheService, messageRepository, studentLease);

        service.send(caller, command(QUESTION));

        order.verify(entitlementGate).assertUnlocked(caller, null);
        order.verify(sessionService).requireNotOwnedByOther(sessionId, STUDENT);
        order.verify(gate).lockStudent(STUDENT);
        order.verify(cacheService).find(key());
        order.verify(sessionService).claim(sessionId, STUDENT, NOW);
        order.verify(messageRepository).save(any(ChatMessage.class));
        order.verify(studentLease).close();
    }

    @Test
    void aHitIsNotWrittenBackToTheCache() {
        cached("cached");

        service.send(caller, command(QUESTION));

        verify(cacheService, never()).storeQuietly(any(), any(), any());
    }

    @Test
    void aHitStillRespectsAnotherStudentsOwnershipOfTheSession() {
        cached("cached");
        doThrow(new com.pte.chat.internal.exception.ChatSessionNotFoundException())
                .when(sessionService).requireNotOwnedByOther(sessionId, STUDENT);

        assertThatThrownBy(() -> service.send(caller, command(QUESTION)))
                .isInstanceOf(com.pte.chat.internal.exception.ChatSessionNotFoundException.class);

        verifyNoInteractions(cacheService, messageRepository, client);
    }

    @Test
    void ifAHitCannotBeStoredTheStudentGetsBusyAndNothingWasChargedSoNothingIsRefunded() {
        cached("cached");
        when(messageRepository.save(any(ChatMessage.class))).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> service.send(caller, command(QUESTION))).isInstanceOf(ChatBusyException.class);

        verify(quotaService, never()).refundQuietly(any());
        verify(studentLease, times(1)).close();
    }

    // ---- a miss -------------------------------------------------------------------------------------------

    @Test
    void aMissGoesOnToTheProviderAndStoresTheAnswerInTheCacheAfterTheMessage() {
        InOrder order = inOrder(cacheService, client, messageRepository);

        ChatReply reply = service.send(caller, command("  " + QUESTION + " ", null, "en"));

        order.verify(cacheService).find(key());
        order.verify(client).generate(any(), any(), any());
        order.verify(messageRepository).save(any(ChatMessage.class));
        order.verify(cacheService).storeQuietly(key(), "**Use** has/have + V3.", SERVED_MODEL);
        assertThat(reply.fromCache()).isFalse();
    }

    @Test
    void theCacheKeySeparatesLanguageAndTaskType() {
        service.send(caller, command(QUESTION, "READ_ALOUD", "vi"));

        verify(cacheService).find(ChatCacheKey.of(QUESTION, "vi", "READ_ALOUD", ChatPrompts.VERSION));
        verify(cacheService).storeQuietly(ChatCacheKey.of(QUESTION, "vi", "READ_ALOUD", ChatPrompts.VERSION),
                "**Use** has/have + V3.", SERVED_MODEL);
    }

    // ---- a session that already has an answer -------------------------------------------------------------

    @Test
    void aSessionThatAlreadyHasAnAnswerNeverConsultsTheCache() {
        when(sessionService.hasAnswers(sessionId, STUDENT)).thenReturn(true);

        ChatReply reply = service.send(caller, command(QUESTION));

        verify(cacheService, never()).find(any());
        verify(client, times(1)).generate(any(), any(), any());
        assertThat(reply.fromCache()).isFalse();
    }

    @Test
    void aSessionThatAlreadyHasAnAnswerNeverWritesTheCacheEither() {
        when(sessionService.hasAnswers(sessionId, STUDENT)).thenReturn(true);

        service.send(caller, command(QUESTION));

        verify(cacheService, never()).storeQuietly(any(), any(), any());
        verifyNoInteractions(cacheService);
    }

    @Test
    void aCachedAnswerExistingForTheQuestionIsIgnoredWhenTheSessionAlreadyHasAnAnswer() {
        when(sessionService.hasAnswers(sessionId, STUDENT)).thenReturn(true);
        when(cacheService.find(any())).thenReturn(Optional.of("stale cached"));

        ChatReply reply = service.send(caller, command(QUESTION));

        assertThat(reply.markdown()).isEqualTo("**Use** has/have + V3.");
        assertThat(reply.fromCache()).isFalse();
    }

    // ---- what may be written to the cache ----------------------------------------------------------------

    @Test
    void anOffTopicAnswerIsNeverCached() {
        when(client.generate(any(), any(), any())).thenReturn(offTopicAnswer());

        service.send(caller, command(QUESTION));

        verify(cacheService, never()).storeQuietly(any(), any(), any());
    }

    @Test
    void anAnswerWhoseMessageCouldNotBeStoredIsNeverCached() {
        when(messageRepository.save(any(ChatMessage.class))).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> service.send(caller, command(QUESTION))).isInstanceOf(ChatBusyException.class);

        verify(cacheService, never()).storeQuietly(any(), any(), any());
    }

    @Test
    void aFailingCacheWriteNeverBreaksTheReplyOrTriggersARefund() {
        doThrow(new IllegalStateException("cache boom")).when(cacheService).storeQuietly(any(), any(), any());

        ChatReply reply = service.send(caller, command(QUESTION));

        assertThat(reply.markdown()).isEqualTo("**Use** has/have + V3.");
        assertThat(reply.onTopic()).isTrue();
        verify(quotaService, never()).refundQuietly(any());
        verify(slotLease, times(1)).close();
        verify(studentLease, times(1)).close();
    }
}
