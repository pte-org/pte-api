package com.pte.chat.internal.exception;

import com.pte.chat.internal.constant.ChatConstants;
import com.pte.chat.internal.service.quota.ChatQuotaSnapshot;
import com.pte.shared.exception.DomainException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The stable HTTP contract of the chat errors: status, machine code, display copy and the 429 payload. */
class ChatExceptionsTest {

    private static final Instant RESET_AT = Instant.parse("2026-10-28T17:00:00Z");

    private static List<DomainException> all() {
        return List.of(
                new ChatQuotaExceededException(new ChatQuotaSnapshot(20, 0, 20, RESET_AT)),
                new ChatRequestInFlightException(),
                new ChatBusyException(),
                new ChatInvalidRequestException("message is too long"));
    }

    @Test
    void quotaExceededIs429WithTheStableCode() {
        DomainException ex = new ChatQuotaExceededException(new ChatQuotaSnapshot(20, 0, 20, RESET_AT));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(ex.getCode()).isEqualTo(ChatConstants.ERR_QUOTA_EXCEEDED);
    }

    @Test
    @SuppressWarnings("unchecked")
    void quotaExceededCarriesResetAtLimitAndRemainingZeroAsTheResponseData() {
        DomainException ex = new ChatQuotaExceededException(new ChatQuotaSnapshot(20, 0, 20, RESET_AT));

        assertThat(ex.getData()).isInstanceOf(Map.class);
        Map<String, Object> data = (Map<String, Object>) ex.getData();
        assertThat(data).containsOnlyKeys("resetAt", "limit", "remaining");
        assertThat(data.get("resetAt")).isEqualTo("2026-10-28T17:00:00Z");
        assertThat(data.get("limit")).isEqualTo(20);
        assertThat(data.get("remaining")).isEqualTo(0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void quotaExceededDataFollowsTheSnapshot() {
        DomainException ex = new ChatQuotaExceededException(
                new ChatQuotaSnapshot(5, 0, 5, Instant.parse("2026-12-31T17:00:00Z")));

        Map<String, Object> data = (Map<String, Object>) ex.getData();
        assertThat(data.get("resetAt")).isEqualTo("2026-12-31T17:00:00Z");
        assertThat(data.get("limit")).isEqualTo(5);
        assertThat(data.get("remaining")).isEqualTo(0);
    }

    @Test
    void requestInFlightIs409() {
        DomainException ex = new ChatRequestInFlightException();

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getCode()).isEqualTo(ChatConstants.ERR_REQUEST_IN_FLIGHT);
        assertThat(ex.getData()).isNull();
    }

    @Test
    void busyIs503() {
        DomainException ex = new ChatBusyException();

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(ex.getCode()).isEqualTo(ChatConstants.ERR_BUSY);
        assertThat(ex.getData()).isNull();
    }

    @Test
    void invalidRequestIs400() {
        DomainException ex = new ChatInvalidRequestException("message is too long");

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getCode()).isEqualTo(ChatConstants.ERR_INVALID_REQUEST);
    }

    @Test
    void everyErrorHasEnglishDisplayCopy() {
        for (DomainException ex : all()) {
            assertThat(ex.getUserMessage()).as(ex.getClass().getSimpleName()).isNotBlank();
            assertThat(ex.getUserMessage()).as(ex.getClass().getSimpleName()).matches("[\\x20-\\x7E]+");
        }
    }

    @Test
    void theDisplayCopyOfDifferentErrorsDiffers() {
        long distinct = all().stream().map(DomainException::getUserMessage).distinct().count();

        assertThat(distinct).isEqualTo(all().size());
    }
}
