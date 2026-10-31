package com.pte.chat.internal.service.consent;

import com.pte.chat.internal.constant.ChatConstants;
import com.pte.chat.internal.exception.ChatConsentRequiredException;
import com.pte.chat.internal.exception.ChatInvalidRequestException;
import com.pte.chat.support.ChatPostgresJpaTest;
import com.pte.chat.support.ChatTestData;
import com.pte.chat.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** One recorded consent per student and policy version; chat is closed until it exists for the current version. */
@ChatPostgresJpaTest
class ChatConsentServicePostgresTest {

    private static final Instant NOW = Instant.parse("2026-10-29T10:00:00Z");
    private static final String CURRENT = ChatConstants.CONSENT_POLICY_VERSION;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private MutableClock clock;
    private ChatConsentService service;
    private ChatTestData data;
    private UUID student;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(NOW);
        service = new ChatConsentService(jdbc, clock);
        data = new ChatTestData(jdbc);
        data.truncateAll();
        student = UUID.randomUUID();
    }

    @Test
    void aStudentWhoNeverConsentedIsNotConsented() {
        ChatConsentStatus status = service.status(student);

        assertThat(status.consented()).isFalse();
        assertThat(status.policyVersion()).isEqualTo(CURRENT);
        assertThat(status.consentedAt()).isNull();
    }

    @Test
    void requireRefusesAStudentWhoNeverConsentedAndNamesTheVersion() {
        assertThatThrownBy(() -> service.require(student)).isInstanceOf(ChatConsentRequiredException.class)
                .satisfies(ex -> assertThat(((ChatConsentRequiredException) ex).getStatus().value()).isEqualTo(403));
    }

    @Test
    void givingConsentRecordsTheVersionTheTimeAndTheAgeConfirmation() {
        ChatConsentStatus status = service.give(student, CURRENT, true);

        assertThat(status.consented()).isTrue();
        assertThat(status.consentedAt()).isEqualTo(NOW);
        assertThat(data.consentRows(student)).isEqualTo(1);
        assertThat(data.consentRow(student)).containsEntry("policy_version", CURRENT).containsEntry("age_confirmed", true);
    }

    @Test
    void afterConsentRequirePasses() {
        service.give(student, CURRENT, true);

        assertThatCode(() -> service.require(student)).doesNotThrowAnyException();
        assertThat(service.status(student).consented()).isTrue();
    }

    @Test
    void givingConsentTwiceKeepsOneRowAndUpdatesTheTime() {
        service.give(student, CURRENT, true);
        clock.set(NOW.plusSeconds(3600));

        ChatConsentStatus status = service.give(student, CURRENT, true);

        assertThat(data.consentRows(student)).isEqualTo(1);
        assertThat(status.consentedAt()).isEqualTo(NOW.plusSeconds(3600));
    }

    @Test
    void consentToAnOlderPolicyVersionIsNotConsentToTheCurrentOne() {
        data.insertConsent(student, "2020-01-01", NOW.minusSeconds(86_400));

        assertThat(service.status(student).consented()).isFalse();
        assertThatThrownBy(() -> service.require(student)).isInstanceOf(ChatConsentRequiredException.class);
    }

    @Test
    void consentingAgainToTheCurrentVersionReplacesAnOldRow() {
        data.insertConsent(student, "2020-01-01", NOW.minusSeconds(86_400));

        service.give(student, CURRENT, true);

        assertThat(data.consentRows(student)).isEqualTo(1);
        assertThat(service.status(student).consented()).isTrue();
    }

    @Test
    void aWrongPolicyVersionIsRejectedAndNothingIsStored() {
        assertThatThrownBy(() -> service.give(student, "2020-01-01", true))
                .isInstanceOf(ChatInvalidRequestException.class);

        assertThat(data.consentRows(student)).isZero();
    }

    @Test
    void withoutTheAgeConfirmationNothingIsStored() {
        assertThatThrownBy(() -> service.give(student, CURRENT, false)).isInstanceOf(ChatInvalidRequestException.class);

        assertThat(data.consentRows(student)).isZero();
    }

    @Test
    void oneStudentsConsentDoesNotOpenChatForAnother() {
        service.give(student, CURRENT, true);

        assertThatThrownBy(() -> service.require(UUID.randomUUID())).isInstanceOf(ChatConsentRequiredException.class);
    }

    @Test
    void withdrawingRemovesTheConsentAndClosesChatAgain() {
        service.give(student, CURRENT, true);

        ChatConsentStatus status = service.withdraw(student);

        assertThat(status.consented()).isFalse();
        assertThat(data.consentRows(student)).isZero();
        assertThatThrownBy(() -> service.require(student)).isInstanceOf(ChatConsentRequiredException.class);
    }

    @Test
    void withdrawingWithoutAConsentIsHarmless() {
        assertThatCode(() -> service.withdraw(student)).doesNotThrowAnyException();
        assertThat(service.status(student).consented()).isFalse();
    }

    @Test
    void withdrawingTouchesOnlyThatStudent() {
        UUID other = UUID.randomUUID();
        service.give(student, CURRENT, true);
        service.give(other, CURRENT, true);

        service.withdraw(student);

        assertThat(service.status(other).consented()).isTrue();
    }

    @Test
    void aStudentCanConsentAgainAfterWithdrawing() {
        service.give(student, CURRENT, true);
        service.withdraw(student);

        service.give(student, CURRENT, true);

        assertThat(service.status(student).consented()).isTrue();
    }

    @Test
    void theRetentionJobNeverTouchesConsentRows() {
        service.give(student, CURRENT, true);
        data.execute("UPDATE chat_consent SET consented_at = now() - interval '5 years'");

        assertThat(service.status(student).consented()).isTrue();
    }
}
