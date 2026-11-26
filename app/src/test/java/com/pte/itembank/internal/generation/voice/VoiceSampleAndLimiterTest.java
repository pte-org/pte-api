package com.pte.itembank.internal.generation.voice;

import com.pte.itembank.internal.generation.config.QuestionGenerationProperties;
import com.pte.itembank.internal.generation.constant.VoiceConstants;
import com.pte.itembank.internal.generation.exception.GenerationMediaException;
import com.pte.itembank.internal.generation.media.EnabledVoices;
import com.pte.itembank.internal.generation.media.KokoroSpeechClient;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VoiceSampleAndLimiterTest {

    private final KokoroSpeechClient kokoro = mock(KokoroSpeechClient.class);
    private final MutableClock clock = new MutableClock();
    private final VoiceSampleService samples = new VoiceSampleService(
            new EnabledVoices(new QuestionGenerationProperties(), new MockEnvironment()), kokoro,
            new VoiceChangeLimiter(), clock);

    @Test
    void aVoiceIsSpokenOnceAndKept() {
        when(kokoro.synthesize(anyString(), eq("af_heart"), anyDouble())).thenReturn(new byte[] {9});

        assertThat(samples.sample("af_heart")).containsExactly(9);
        assertThat(samples.sample("af_heart")).containsExactly(9);

        verify(kokoro, times(1)).synthesize(anyString(), eq("af_heart"), anyDouble());
    }

    @Test
    void aVoiceThatIsNotInTheCatalogIsRefusedWithoutCallingTheSpeechServer() {
        assertThatThrownBy(() -> samples.sample("../etc/passwd")).isInstanceOf(VoiceChangeException.class);
        verify(kokoro, never()).synthesize(anyString(), anyString(), anyDouble());
    }

    @Test
    void aFailureIsNotAskedAgainRightAwayButIsAfterAMinute() {
        when(kokoro.synthesize(anyString(), eq("af_heart"), anyDouble()))
                .thenThrow(new IllegalStateException("down at https://secret-host"))
                .thenReturn(new byte[] {7});

        assertThatThrownBy(() -> samples.sample("af_heart")).isInstanceOf(GenerationMediaException.class)
                .hasMessageNotContaining("secret-host");
        assertThatThrownBy(() -> samples.sample("af_heart")).isInstanceOf(GenerationMediaException.class);
        verify(kokoro, times(1)).synthesize(anyString(), eq("af_heart"), anyDouble());

        clock.advanceSeconds(VoiceConstants.SAMPLE_FAILURE_CACHE_SECONDS + 1);

        assertThat(samples.sample("af_heart")).containsExactly(7);
    }

    @Test
    void oneSpeechCallServesPeopleAskingForTheSameVoiceTogether() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        when(kokoro.synthesize(anyString(), eq("bf_emma"), anyDouble())).thenAnswer(call -> {
            calls.incrementAndGet();
            started.countDown();
            release.await();
            return new byte[] {5};
        });
        var first = java.util.concurrent.CompletableFuture.supplyAsync(() -> samples.sample("bf_emma"));
        started.await();
        var second = java.util.concurrent.CompletableFuture.supplyAsync(() -> samples.sample("bf_emma"));
        Thread.sleep(50);
        release.countDown();

        assertThat(first.get()).containsExactly(5);
        assertThat(second.get()).containsExactly(5);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void aPersonCanHaveOnlyOneChangeRunningAtATime() {
        var limiter = new VoiceChangeLimiter();
        UUID person = UUID.randomUUID();

        String result = limiter.run(person, () -> {
            assertThatThrownBy(() -> limiter.run(person, () -> "second")).isInstanceOfSatisfying(
                    VoiceChangeException.class, e -> assertThat(e.getMessage())
                            .isEqualTo(VoiceConstants.GENERATION_VOICE_BUSY));
            return "first";
        });

        assertThat(result).isEqualTo("first");
        assertThat(limiter.run(person, () -> "again")).isEqualTo("again");
    }

    @Test
    void onlyAFewSpeechCallsRunAcrossThePeople() {
        var limiter = new VoiceChangeLimiter();
        limiter.run(UUID.randomUUID(), () -> limiter.run(UUID.randomUUID(), () ->
                assertThatThrownBy(() -> limiter.run(UUID.randomUUID(), () -> "third"))
                        .isInstanceOf(VoiceChangeException.class)));
    }

    @Test
    void aFailedWorkReleasesItsPlace() {
        var limiter = new VoiceChangeLimiter();
        UUID person = UUID.randomUUID();
        assertThatThrownBy(() -> limiter.run(person, () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(limiter.run(person, () -> "ok")).isEqualTo("ok");
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-26T10:00:00Z");

        void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
