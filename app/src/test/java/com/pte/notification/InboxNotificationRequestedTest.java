package com.pte.notification;

import com.pte.notification.domain.enums.InboxCategory;
import com.pte.notification.domain.enums.InboxImportance;
import com.pte.notification.domain.enums.InboxNotificationType;
import com.pte.notification.domain.enums.InboxTargetType;
import com.pte.notification.internal.exception.InboxNotificationException;
import com.pte.shared.exception.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InboxNotificationRequestedTest {
    private static final UUID USER = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID TENANT = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID TARGET = UUID.fromString("30000000-0000-0000-0000-000000000001");

    private static InboxNotificationRequested request(int version, String key, String title, String body,
                                                       List<InboxRecipient> recipients) {
        return new InboxNotificationRequested(version, key, InboxNotificationType.SESSION_CLOSING_SOON,
                InboxCategory.SESSION, InboxImportance.INFO, title, body, InboxTargetType.SESSION,
                TARGET, recipients);
    }

    private static List<InboxRecipient> host() {
        return List.of(new InboxRecipient(USER, TENANT));
    }

    @Test
    void acceptsInclusiveLengthBoundariesAndSchemaVersionOne() {
        assertThat(request(1, "k", "t", "b", host()).schemaVersion()).isEqualTo(1);
        InboxNotificationRequested maximum = request(1, "k".repeat(255), "t".repeat(150),
                "b".repeat(5000), host());
        assertThat(maximum.eventKey()).hasSize(255);
        assertThat(maximum.title()).hasSize(150);
        assertThat(maximum.body()).hasSize(5000);
    }

    static Stream<Object[]> invalidLengths() {
        return Stream.of(new Object[]{0, "key", "Title", "Body"},
                new Object[]{2, "key", "Title", "Body"},
                new Object[]{1, null, "Title", "Body"},
                new Object[]{1, "", "Title", "Body"},
                new Object[]{1, " ", "Title", "Body"},
                new Object[]{1, "k".repeat(256), "Title", "Body"},
                new Object[]{1, "key", null, "Body"},
                new Object[]{1, "key", "", "Body"},
                new Object[]{1, "key", " ", "Body"},
                new Object[]{1, "key", "t".repeat(151), "Body"},
                new Object[]{1, "key", "Title", null},
                new Object[]{1, "key", "Title", ""},
                new Object[]{1, "key", "Title", " "},
                new Object[]{1, "key", "Title", "b".repeat(5001)});
    }

    @ParameterizedTest
    @MethodSource("invalidLengths")
    void rejectsInvalidVersionOrTextWithSafeDomainError(int version, String key, String title, String body) {
        assertThatExceptionOfType(InboxNotificationException.class)
                .isThrownBy(() -> request(version, key, title, body, host()))
                .satisfies(error -> {
                    assertThat(error).isInstanceOf(DomainException.class);
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(error.getCode()).isEqualTo("INBOX_INVALID_REQUEST");
                    assertThat(error.getUserMessage()).isNotBlank();
                    assertThat(error.getUserMessage()).doesNotContain("Exception", "java.");
                });
    }

    @Test
    void rejectsNullContractFieldsAndNullRecipientList() {
        assertInvalid(null, InboxCategory.SESSION, InboxImportance.INFO, InboxTargetType.SESSION, TARGET, host());
        assertInvalid(InboxNotificationType.SESSION_CLOSING_SOON, null, InboxImportance.INFO,
                InboxTargetType.SESSION, TARGET, host());
        assertInvalid(InboxNotificationType.SESSION_CLOSING_SOON, InboxCategory.SESSION, null,
                InboxTargetType.SESSION, TARGET, host());
        assertInvalid(InboxNotificationType.SESSION_CLOSING_SOON, InboxCategory.SESSION, InboxImportance.INFO,
                null, TARGET, host());
        assertInvalid(InboxNotificationType.SESSION_CLOSING_SOON, InboxCategory.SESSION, InboxImportance.INFO,
                InboxTargetType.SESSION, null, host());
        assertThatExceptionOfType(InboxNotificationException.class)
                .isThrownBy(() -> request(1, "key", "Title", "Body", null));
        List<InboxRecipient> nullEntry = new ArrayList<>();
        nullEntry.add(null);
        assertThatExceptionOfType(InboxNotificationException.class)
                .isThrownBy(() -> request(1, "key", "Title", "Body", nullEntry));
    }

    @Test
    void recipientRequiresUserIdentity() {
        assertThatExceptionOfType(InboxNotificationException.class)
                .isThrownBy(() -> new InboxRecipient(null, TENANT));
    }

    @Test
    void normalizesExactDuplicatesAndDefensivelyCopiesAudience() {
        InboxRecipient recipient = new InboxRecipient(USER, TENANT);
        List<InboxRecipient> audience = new ArrayList<>(List.of(recipient, recipient));
        InboxNotificationRequested event = request(1, "key", "Title", "Body", audience);
        audience.clear();
        assertThat(event.recipients()).containsExactly(recipient);
        assertThatThrownBy(() -> event.recipients().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsSameUserWithConflictingTenantOwnership() {
        assertThatExceptionOfType(InboxNotificationException.class).isThrownBy(() -> request(1, "key", "Title",
                "Body", List.of(new InboxRecipient(USER, TENANT), new InboxRecipient(USER, UUID.randomUUID()))));
    }

    @Test
    void applicationAudienceMustBePlatformScopedAndHostAudienceMustBeTenantScoped() {
        InboxRecipient admin = new InboxRecipient(USER, null);
        assertThat(new InboxNotificationRequested(1, "application:submitted", InboxNotificationType.APPLICATION_SUBMITTED,
                InboxCategory.APPLICATION, InboxImportance.INFO, "New application", "Review application",
                InboxTargetType.APPLICATION, TARGET, List.of(admin)).recipients()).containsExactly(admin);
        assertInvalid(InboxNotificationType.APPLICATION_SUBMITTED, InboxCategory.APPLICATION, InboxImportance.INFO,
                InboxTargetType.APPLICATION, TARGET, host());
        assertThatExceptionOfType(InboxNotificationException.class)
                .isThrownBy(() -> request(1, "key", "Title", "Body", List.of(admin)));
    }

    static Stream<Object[]> validMappings() {
        return Stream.of(
                new Object[]{InboxNotificationType.PLATFORM_ANNOUNCEMENT, InboxCategory.SYSTEM_NOTICE, InboxTargetType.ANNOUNCEMENT},
                new Object[]{InboxNotificationType.PLATFORM_ANNOUNCEMENT, InboxCategory.MAINTENANCE, InboxTargetType.ANNOUNCEMENT},
                new Object[]{InboxNotificationType.SESSION_CLOSING_SOON, InboxCategory.SESSION, InboxTargetType.SESSION},
                new Object[]{InboxNotificationType.SESSION_GRADING_COMPLETED, InboxCategory.SESSION, InboxTargetType.SESSION},
                new Object[]{InboxNotificationType.COMMERCIAL_OUTCOME_CONFIRMED, InboxCategory.BILLING, InboxTargetType.SUBSCRIPTION},
                new Object[]{InboxNotificationType.COMMERCIAL_OUTCOME_CONFIRMED, InboxCategory.BILLING, InboxTargetType.QUOTA},
                new Object[]{InboxNotificationType.COMMERCIAL_OUTCOME_CONFIRMED, InboxCategory.BILLING, InboxTargetType.ORDER},
                new Object[]{InboxNotificationType.ORDER_EXPIRED, InboxCategory.BILLING, InboxTargetType.ORDER},
                new Object[]{InboxNotificationType.SUBSCRIPTION_REVOKED, InboxCategory.BILLING, InboxTargetType.SUBSCRIPTION});
    }

    @ParameterizedTest
    @MethodSource("validMappings")
    void acceptsApprovedHostTypeCategoryTargetMappings(InboxNotificationType type, InboxCategory category,
                                                       InboxTargetType targetType) {
        assertThat(new InboxNotificationRequested(1, "key", type, category, InboxImportance.IMPORTANT,
                "Title", "Body", targetType, TARGET, host()).type()).isEqualTo(type);
    }

    @Test
    void rejectsMismatchedTypeCategoryAndTarget() {
        assertInvalid(InboxNotificationType.SESSION_CLOSING_SOON, InboxCategory.BILLING, InboxImportance.INFO,
                InboxTargetType.SESSION, TARGET, host());
        assertInvalid(InboxNotificationType.SESSION_GRADING_COMPLETED, InboxCategory.SESSION, InboxImportance.INFO,
                InboxTargetType.ORDER, TARGET, host());
        assertInvalid(InboxNotificationType.ORDER_EXPIRED, InboxCategory.BILLING, InboxImportance.INFO,
                InboxTargetType.SUBSCRIPTION, TARGET, host());
        assertInvalid(InboxNotificationType.SUBSCRIPTION_REVOKED, InboxCategory.SESSION, InboxImportance.INFO,
                InboxTargetType.SUBSCRIPTION, TARGET, host());
        assertInvalid(InboxNotificationType.PLATFORM_ANNOUNCEMENT, InboxCategory.APPLICATION, InboxImportance.INFO,
                InboxTargetType.ANNOUNCEMENT, TARGET, host());
        assertInvalid(InboxNotificationType.COMMERCIAL_OUTCOME_CONFIRMED, InboxCategory.BILLING, InboxImportance.INFO,
                InboxTargetType.APPLICATION, TARGET, host());
    }

    private static void assertInvalid(InboxNotificationType type, InboxCategory category, InboxImportance importance,
                                      InboxTargetType targetType, UUID targetId, List<InboxRecipient> recipients) {
        assertThatExceptionOfType(InboxNotificationException.class).isThrownBy(() ->
                new InboxNotificationRequested(1, "key", type, category, importance, "Title", "Body",
                        targetType, targetId, recipients));
    }
}
