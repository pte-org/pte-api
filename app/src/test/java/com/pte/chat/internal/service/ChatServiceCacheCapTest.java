package com.pte.chat.internal.service;

import com.pte.chat.internal.prompt.ChatPrompts;
import com.pte.chat.internal.service.cache.ChatCacheKey;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The daily cap on free cache hits (quality remediation): at or past the cap the cache is not consulted and the
 * question takes the normal charged path; below it a hit is free. The day starts at 00:00 Vietnam time.
 */
class ChatServiceCacheCapTest extends ChatServiceTestSupport {

    private static final String QUESTION = "How do I use the present perfect?";

    private String key() {
        return ChatCacheKey.of(QUESTION, "en", null, ChatPrompts.VERSION);
    }

    private void hitsToday(int hits) {
        when(sessionService.cacheHitsSince(eq(STUDENT), any(Instant.class))).thenReturn(hits);
    }

    private Instant sinceAskedFor() {
        ArgumentCaptor<Instant> since = ArgumentCaptor.forClass(Instant.class);
        verify(sessionService).cacheHitsSince(eq(STUDENT), since.capture());
        return since.getValue();
    }

    // ---- at or past the cap ---------------------------------------------------------------------------------

    @Test
    void atTheCapTheCacheIsNotConsultedAndTheQuestionIsChargedAndCached() {
        properties.setMaxCacheHitsPerDay(5);
        hitsToday(5);
        when(cacheService.find(any())).thenReturn(Optional.of("cached answer"));

        ChatReply reply = service.send(caller, command(QUESTION));

        verify(cacheService, never()).find(any());
        verify(quotaService).consume(STUDENT);
        verify(client, times(1)).generate(any(), any(), any());
        assertThat(reply.fromCache()).isFalse();
        assertThat(reply.markdown()).isEqualTo("**Use** has/have + V3.");
        assertThat(savedMessage().isFromCache()).isFalse();
        verify(cacheService).storeQuietly(key(), "**Use** has/have + V3.", SERVED_MODEL);
    }

    @Test
    void wellPastTheCapBehavesTheSame() {
        properties.setMaxCacheHitsPerDay(5);
        hitsToday(500);

        ChatReply reply = service.send(caller, command(QUESTION));

        verify(cacheService, never()).find(any());
        verify(quotaService).consume(STUDENT);
        assertThat(reply.fromCache()).isFalse();
    }

    @Test
    void aCapOfOneIsReachedAfterOneHit() {
        properties.setMaxCacheHitsPerDay(1);
        hitsToday(1);

        service.send(caller, command(QUESTION));

        verify(cacheService, never()).find(any());
        verify(quotaService).consume(STUDENT);
        verify(client, times(1)).generate(any(), any(), any());
    }

    // ---- below the cap --------------------------------------------------------------------------------------

    @Test
    void oneBelowTheCapTheCacheIsConsultedAndAHitIsFree() {
        properties.setMaxCacheHitsPerDay(5);
        hitsToday(4);
        when(cacheService.find(key())).thenReturn(Optional.of("cached answer"));

        ChatReply reply = service.send(caller, command(QUESTION));

        verify(cacheService).find(key());
        verify(quotaService, never()).consume(any());
        verify(client, never()).generate(any(), any(), any());
        assertThat(reply.fromCache()).isTrue();
        assertThat(reply.markdown()).isEqualTo("cached answer");
        assertThat(savedMessage().isFromCache()).isTrue();
    }

    @Test
    void aCapOfOneWithNoHitsYetStillAllowsTheFirstHit() {
        properties.setMaxCacheHitsPerDay(1);
        hitsToday(0);
        when(cacheService.find(key())).thenReturn(Optional.of("cached answer"));

        ChatReply reply = service.send(caller, command(QUESTION));

        assertThat(reply.fromCache()).isTrue();
        verify(quotaService, never()).consume(any());
    }

    @Test
    void belowTheCapAMissIsStillChargedAsBefore() {
        properties.setMaxCacheHitsPerDay(5);
        hitsToday(0);

        ChatReply reply = service.send(caller, command(QUESTION));

        verify(cacheService).find(key());
        verify(quotaService).consume(STUDENT);
        assertThat(reply.fromCache()).isFalse();
    }

    @Test
    void theDefaultCapIsOneHundred() {
        hitsToday(99);
        when(cacheService.find(key())).thenReturn(Optional.of("cached answer"));
        assertThat(service.send(caller, command(QUESTION)).fromCache()).isTrue();

        hitsToday(100);
        ChatReply reply = service.send(caller, command(QUESTION));
        assertThat(reply.fromCache()).isFalse();
    }

    // ---- the counting window ---------------------------------------------------------------------------------

    @Test
    void theWindowStartsAtMidnightInVietnamOfTheCurrentVietnamDay() {
        // 20:00Z on 28 Oct is 03:00 on 29 Oct in Vietnam, so the day started at 17:00Z on 28 Oct.
        clock.set(Instant.parse("2026-10-28T20:00:00Z"));

        service.send(caller, command(QUESTION));

        assertThat(sinceAskedFor()).isEqualTo(Instant.parse("2026-10-28T17:00:00Z"));
    }

    @Test
    void justBeforeMidnightInVietnamTheWindowIsStillTheSameDay() {
        // 16:59:59Z on 28 Oct is 23:59:59 on 28 Oct in Vietnam.
        clock.set(Instant.parse("2026-10-28T16:59:59Z"));

        service.send(caller, command(QUESTION));

        assertThat(sinceAskedFor()).isEqualTo(Instant.parse("2026-10-27T17:00:00Z"));
    }

    @Test
    void atExactlyMidnightInVietnamANewWindowBegins() {
        clock.set(Instant.parse("2026-10-28T17:00:00Z"));

        service.send(caller, command(QUESTION));

        assertThat(sinceAskedFor()).isEqualTo(Instant.parse("2026-10-28T17:00:00Z"));
    }

    @Test
    void theWindowIsAskedForTheCallingStudentOnly() {
        service.send(caller, command(QUESTION));

        verify(sessionService).cacheHitsSince(eq(STUDENT), any(Instant.class));
    }

    @Test
    void aSessionThatAlreadyHasAnAnswerNeverAsksForTheCapAtAll() {
        when(sessionService.hasAnswers(sessionId, STUDENT)).thenReturn(true);

        service.send(caller, command(QUESTION));

        verify(sessionService, never()).cacheHitsSince(any(), any());
        verify(cacheService, never()).find(any());
    }
}
