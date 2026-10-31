package com.pte.chat.internal.service.quota;

import com.pte.chat.internal.config.ChatAiProperties;
import com.pte.chat.internal.constant.ChatConstants;
import com.pte.chat.internal.exception.ChatQuotaExceededException;
import com.pte.chat.internal.service.ChatObservability;
import com.pte.chat.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Date and reset arithmetic of the quota, with the SQL replaced by a mock. The server's clock picks the Vietnam
 * day; a charge always remembers its own day so a refund or a response after midnight still uses it.
 */
class ChatQuotaServiceTest {

    private static final UUID STUDENT = UUID.randomUUID();
    /** 17:00 in Ho Chi Minh City on 2026-10-28. */
    private static final Instant NOON_UTC = Instant.parse("2026-10-28T10:00:00Z");
    private static final LocalDate DAY = LocalDate.of(2026, 10, 28);
    private static final Instant DAY_RESET = Instant.parse("2026-10-28T17:00:00Z");
    private static final Instant NEXT_DAY_RESET = Instant.parse("2026-10-29T17:00:00Z");

    private final ChatQuotaRepository repository = mock(ChatQuotaRepository.class);
    private final ChatObservability observability = mock(ChatObservability.class);
    private final ChatAiProperties properties = new ChatAiProperties();
    private MutableClock clock;
    private ChatQuotaService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(NOON_UTC);
        properties.setDailyLimit(20);
        service = new ChatQuotaService(repository, properties, clock, observability);
    }

    // ---- consume -----------------------------------------------------------------------------------------

    @Test
    void consumeChargesTheVietnamDayWithTheConfiguredLimitAndReturnsATicketForThatDay() {
        when(repository.consume(STUDENT, DAY, 20)).thenReturn(OptionalInt.of(1));

        ChatQuotaTicket ticket = service.consume(STUDENT);

        assertThat(ticket).isEqualTo(new ChatQuotaTicket(STUDENT, DAY));
        verify(repository).consume(STUDENT, DAY, 20);
    }

    @Test
    void consumeUsesTheConfiguredDailyLimit() {
        properties.setDailyLimit(3);
        service = new ChatQuotaService(repository, properties, clock, observability);
        when(repository.consume(STUDENT, DAY, 3)).thenReturn(OptionalInt.of(1));

        service.consume(STUDENT);

        verify(repository).consume(STUDENT, DAY, 3);
    }

    @Test
    void theDayRollsOverAtVietnamMidnightNotUtcMidnight() {
        when(repository.consume(any(), any(), anyInt())).thenReturn(OptionalInt.of(1));

        clock.set(Instant.parse("2026-10-28T16:59:59Z"));
        assertThat(service.consume(STUDENT).usageDate()).isEqualTo(LocalDate.of(2026, 10, 28));

        clock.set(Instant.parse("2026-10-28T17:00:00Z"));
        assertThat(service.consume(STUDENT).usageDate()).isEqualTo(LocalDate.of(2026, 10, 29));

        // 02:00 UTC is already 09:00 the same calendar day in Vietnam
        clock.set(Instant.parse("2026-10-28T02:00:00Z"));
        assertThat(service.consume(STUDENT).usageDate()).isEqualTo(LocalDate.of(2026, 10, 28));
    }

    @Test
    void consumeFollowsTheClockOnEveryCallNotJustAtConstruction() {
        when(repository.consume(any(), any(), anyInt())).thenReturn(OptionalInt.of(1));
        service.consume(STUDENT);

        clock.set(Instant.parse("2026-11-02T10:00:00Z"));
        service.consume(STUDENT);

        verify(repository).consume(STUDENT, LocalDate.of(2026, 11, 2), 20);
    }

    @Test
    void anExhaustedDayThrows429DataWithResetAtLimitAndZeroRemaining() {
        when(repository.consume(STUDENT, DAY, 20)).thenReturn(OptionalInt.empty());

        ChatQuotaExceededException ex =
                catchThrowableOfType(ChatQuotaExceededException.class, () -> service.consume(STUDENT));

        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(ChatConstants.ERR_QUOTA_EXCEEDED);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) ex.getData();
        assertThat(data.get("resetAt")).isEqualTo(DAY_RESET.toString());
        assertThat(data.get("limit")).isEqualTo(20);
        assertThat(data.get("remaining")).isEqualTo(0);
    }

    @Test
    void anExhaustedDayNeverTouchesTheCounterAgain() {
        when(repository.consume(STUDENT, DAY, 20)).thenReturn(OptionalInt.empty());

        assertThatThrownBy(() -> service.consume(STUDENT)).isInstanceOf(ChatQuotaExceededException.class);

        verify(repository, never()).refund(any(), any());
        verify(repository, never()).used(any(), any());
    }

    @Test
    void aDatabaseFailureWhileChargingIsNotMistakenForAnExhaustedQuota() {
        when(repository.consume(STUDENT, DAY, 20)).thenThrow(new DataAccessResourceFailureException("db down"));

        assertThatThrownBy(() -> service.consume(STUDENT)).isInstanceOf(DataAccessResourceFailureException.class);
    }

    // ---- snapshots ---------------------------------------------------------------------------------------

    @Test
    void snapshotOfTodayReportsUsedRemainingLimitAndTheNextMidnight() {
        when(repository.used(STUDENT, DAY)).thenReturn(7);

        ChatQuotaSnapshot snapshot = service.snapshot(STUDENT);

        assertThat(snapshot).isEqualTo(new ChatQuotaSnapshot(7, 13, 20, DAY_RESET));
    }

    @Test
    void snapshotOfAStudentWithoutAnyRowIsFullyAvailable() {
        when(repository.used(STUDENT, DAY)).thenReturn(0);

        assertThat(service.snapshot(STUDENT)).isEqualTo(new ChatQuotaSnapshot(0, 20, 20, DAY_RESET));
    }

    @Test
    void snapshotOfAnExhaustedDayHasZeroRemaining() {
        when(repository.used(STUDENT, DAY)).thenReturn(20);

        ChatQuotaSnapshot snapshot = service.snapshot(STUDENT);

        assertThat(snapshot.remaining()).isZero();
        assertThat(snapshot.limit()).isEqualTo(20);
    }

    @Test
    void remainingNeverGoesNegativeWhenTheLimitWasLoweredBelowTheUsage() {
        when(repository.used(STUDENT, DAY)).thenReturn(25);

        assertThat(service.snapshot(STUDENT).remaining()).isZero();
    }

    @Test
    void snapshotOfATicketIsComputedForTheChargedDayNotForToday() {
        when(repository.consume(STUDENT, DAY, 20)).thenReturn(OptionalInt.of(1));
        ChatQuotaTicket ticket = service.consume(STUDENT);
        // Gemini answers after Vietnam midnight
        clock.set(Instant.parse("2026-10-28T17:00:01Z"));
        when(repository.used(STUDENT, DAY)).thenReturn(1);
        when(repository.used(STUDENT, LocalDate.of(2026, 10, 29))).thenReturn(0);

        ChatQuotaSnapshot charged = service.snapshot(ticket);
        ChatQuotaSnapshot today = service.snapshot(STUDENT);

        assertThat(charged).isEqualTo(new ChatQuotaSnapshot(1, 19, 20, DAY_RESET));
        assertThat(today).isEqualTo(new ChatQuotaSnapshot(0, 20, 20, NEXT_DAY_RESET));
    }

    @Test
    void resetAtIsVietnamMidnightOfTheFollowingDay() {
        when(repository.used(any(), any())).thenReturn(0);
        Instant expected = DAY.plusDays(1).atStartOfDay(ChatConstants.ZONE).toInstant();

        assertThat(service.snapshot(STUDENT).resetAt()).isEqualTo(expected);
    }

    // ---- refund ------------------------------------------------------------------------------------------

    @Test
    void refundGivesBackTheChargedDayEvenAfterMidnight() {
        ChatQuotaTicket ticket = new ChatQuotaTicket(STUDENT, DAY);
        clock.set(Instant.parse("2026-10-28T17:00:05Z"));

        service.refundQuietly(ticket);

        verify(repository).refund(STUDENT, DAY);
        verify(repository, never()).refund(STUDENT, LocalDate.of(2026, 10, 29));
    }

    @Test
    void aSuccessfulRefundIsNotCountedAsAFailure() {
        service.refundQuietly(new ChatQuotaTicket(STUDENT, DAY));

        verify(observability, never()).refundFailed();
    }

    @ParameterizedTest
    @ValueSource(classes = {IllegalStateException.class, NullPointerException.class,
            DataAccessResourceFailureException.class})
    void aFailingRefundNeverThrowsAndIsCounted(Class<? extends RuntimeException> failure) throws Exception {
        RuntimeException error = failure == DataAccessResourceFailureException.class
                ? new DataAccessResourceFailureException("db down")
                : failure.getConstructor().newInstance();
        doThrow(error).when(repository).refund(STUDENT, DAY);

        assertThatCode(() -> service.refundQuietly(new ChatQuotaTicket(STUDENT, DAY))).doesNotThrowAnyException();

        verify(observability).refundFailed();
    }
}
