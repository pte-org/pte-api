package com.pte.itembank.internal.generation.usage;

import com.pte.itembank.internal.generation.config.QuestionGenerationProperties;
import com.pte.itembank.internal.generation.constant.PromptVersionConstants;
import com.pte.itembank.internal.generation.exception.GenerationLimitException;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class PromptTestQuotaTest {

    private static final UUID USER = UUID.randomUUID();

    private final PromptTestUsageCounter counter = mock(PromptTestUsageCounter.class);
    private final QuestionGenerationProperties properties = new QuestionGenerationProperties();

    private PromptTestQuota at(String instant) {
        return new PromptTestQuota(counter, properties, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }

    private void countIs(long runs) {
        when(counter.countRun(any(), any())).thenReturn(runs);
    }

    @Test
    void countsTheCallerOnTheVietnamDay() {
        countIs(1);

        at("2026-11-12T05:00:00Z").consume(USER);

        verify(counter).countRun(USER, LocalDate.of(2026, 11, 12));
        verifyNoMoreInteractions(counter);
    }

    @Test
    void anInstantStillYesterdayInUtcIsAlreadyTodayInVietnam() {
        countIs(1);

        at("2026-11-11T18:30:00Z").consume(USER);

        verify(counter).countRun(USER, LocalDate.of(2026, 11, 12));
    }

    @Test
    void justBeforeVietnamMidnightIsStillTheSameDay() {
        countIs(1);

        at("2026-11-11T16:59:59Z").consume(USER);

        verify(counter).countRun(USER, LocalDate.of(2026, 11, 11));
    }

    @Test
    void exactlyVietnamMidnightStartsTheNextDay() {
        countIs(1);

        at("2026-11-11T17:00:00Z").consume(USER);

        verify(counter).countRun(USER, LocalDate.of(2026, 11, 12));
    }

    @Test
    void theRunThatReachesTheLimitStillPasses() {
        countIs(20);

        assertThatCode(() -> at("2026-11-12T05:00:00Z").consume(USER)).doesNotThrowAnyException();
    }

    @Test
    void theConfiguredLimitIsUsed() {
        properties.getLimits().setMaxPromptTestsPerUserPerDay(7);
        countIs(7);
        assertThatCode(() -> at("2026-11-12T05:00:00Z").consume(USER)).doesNotThrowAnyException();

        countIs(8);
        assertThatThrownBy(() -> at("2026-11-12T05:00:00Z").consume(USER))
                .isInstanceOf(GenerationLimitException.class);
    }

    @Test
    void theRunAfterTheLimitIsRefusedWith429AndTheUserMessage() {
        countIs(21);

        assertRefused();
    }

    @Test
    void aCountFarAboveTheLimitIsRefused() {
        countIs(5000);

        assertRefused();
    }

    private void assertRefused() {
        assertThatThrownBy(() -> at("2026-11-12T05:00:00Z").consume(USER))
                .isInstanceOfSatisfying(GenerationLimitException.class, failure -> {
                    assertThat(failure.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(failure.getCode()).isEqualTo("AI_PROMPT_TEST_DAILY_LIMIT_REACHED");
                    assertThat(failure.getUserMessage())
                            .isEqualTo(PromptVersionConstants.TEST_DAILY_LIMIT_REACHED_MESSAGE);
                });
    }

    @Test
    void whenTheCounterFailsTheRunIsAllowed() {
        when(counter.countRun(any(), any())).thenThrow(new IllegalStateException("no reply"));
        assertThatCode(() -> at("2026-11-12T05:00:00Z").consume(USER)).doesNotThrowAnyException();

        doThrow(new RedisConnectionFailureException("down")).when(counter).countRun(any(), any());
        assertThatCode(() -> at("2026-11-12T05:00:00Z").consume(USER)).doesNotThrowAnyException();
    }

    @Test
    void theDefaultCapIsTwentyAndZeroIsRejected() {
        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        QuestionGenerationProperties.Limits limits = new QuestionGenerationProperties().getLimits();

        assertThat(limits.getMaxPromptTestsPerUserPerDay()).isEqualTo(20);
        assertThat(validator.validate(limits)).isEmpty();
        limits.setMaxPromptTestsPerUserPerDay(0);
        assertThat(validator.validate(limits)).hasSize(1);
    }
}
