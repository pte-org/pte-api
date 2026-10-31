package com.pte.chat.internal.service;

import com.pte.chat.internal.exception.ChatConsentRequiredException;
import com.pte.chat.internal.service.consent.ChatConsentCheck;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** Without a recorded consent a message is refused before anything is locked, charged or sent to the provider. */
class ChatServiceConsentTest extends ChatServiceTestSupport {

    private final ChatConsentCheck consent = mock(ChatConsentCheck.class);

    private ChatService withConsent() {
        return new ChatService(entitlementGate, properties, quotaService, sessionService, gate, cacheService, client,
                messageRepository, observability, clock, consent);
    }

    @Test
    void aStudentWithoutConsentIsRefusedAndNothingIsSpent() {
        doThrow(new ChatConsentRequiredException()).when(consent).require(STUDENT);

        assertThatThrownBy(() -> withConsent().send(caller, command("How do I use it?")))
                .isInstanceOf(ChatConsentRequiredException.class);

        verifyNoInteractions(quotaService, client, cacheService, messageRepository);
        verify(gate, never()).lockStudent(any());
    }

    @Test
    void aStudentWithConsentIsAnsweredAsBefore() {
        ChatReply reply = withConsent().send(caller, command("How do I use the present perfect?"));

        assertThat(reply.onTopic()).isTrue();
        verify(consent).require(STUDENT);
    }

    @Test
    void readingTheQuotaAndTheLatestSessionDoesNotNeedConsent() {
        ChatService withConsent = withConsent();

        withConsent.quota(caller);
        withConsent.latestSession(caller);

        verifyNoInteractions(consent);
    }
}
