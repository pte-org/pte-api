package com.pte.practice.internal.service.session;

import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.practice.internal.domain.PracticeSession;
import com.pte.practice.internal.dto.request.PracticeAnswerRequest;
import com.pte.practice.internal.dto.request.PracticeDraftRequest;
import com.pte.practice.internal.dto.request.PracticeSaveAndExitRequest;
import com.pte.practice.internal.dto.request.PracticeSessionActionRequest;
import com.pte.practice.internal.dto.request.PracticeSessionStartRequest;
import com.pte.practice.internal.exception.PracticeIdempotencyException;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Idempotency-key validation and the canonical request fingerprints compared on replay. */
final class PracticeSessionRequestHashes {

    private PracticeSessionRequestHashes() {
    }

    static String requireIdempotencyKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            throw new PracticeIdempotencyException(HttpStatus.BAD_REQUEST,
                    PracticeConstants.PRACTICE_IDEMPOTENCY_KEY_REQUIRED,
                    PracticeConstants.PRACTICE_IDEMPOTENCY_KEY_REQUIRED_MESSAGE);
        }
        String key = rawKey.trim();
        if (key.length() > PracticeConstants.MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new PracticeIdempotencyException(HttpStatus.BAD_REQUEST,
                    PracticeConstants.PRACTICE_IDEMPOTENCY_KEY_INVALID,
                    PracticeConstants.PRACTICE_IDEMPOTENCY_KEY_INVALID_MESSAGE);
        }
        return key;
    }

    static void assertSameRequest(PracticeSession session, String requestHash) {
        assertSameHash(session.getStartRequestHash(), requestHash);
    }

    private static void assertSameHash(String storedHash, String requestHash) {
        if (!MessageDigest.isEqual(
                storedHash == null ? new byte[0] : storedHash.getBytes(StandardCharsets.UTF_8),
                requestHash.getBytes(StandardCharsets.UTF_8))) {
            throw PracticeIdempotencyException.reused();
        }
    }

    static String startRequestHash(PracticeSessionStartRequest request) {
        return sha256(String.join("\n",
                safe(request.productCode()),
                String.valueOf(request.organizationId()),
                joinSorted(request.safeTaskTypeCodes()),
                safe(request.skillCode()),
                String.valueOf(request.mockTestId()),
                String.valueOf(request.effectiveRestart()),
                joinSorted(request.capabilities() == null ? Set.of() : request.capabilities().safeCapabilities()),
                // Defaults applied first, so omitting the fields and sending the defaults hash the same.
                request.effectiveTimingMode().name(),
                String.valueOf(request.effectiveQuestionCount())));
    }

    static String actionRequestHash(PracticeSessionActionRequest request) {
        return sha256(String.valueOf(request.clientVersion()));
    }

    static String itemActionRequestHash(UUID itemPublicId, PracticeSessionActionRequest request) {
        return sha256(String.join("\n", String.valueOf(itemPublicId),
                String.valueOf(request.clientVersion())));
    }

    static String answerRequestHash(UUID itemPublicId, PracticeAnswerRequest request) {
        return sha256(String.join("\n", String.valueOf(itemPublicId),
                String.valueOf(request.clientVersion()), safe(request.payload()),
                request.confidence() == null ? "" : request.confidence().name()));
    }

    static String draftRequestHash(UUID itemPublicId, PracticeDraftRequest request) {
        return sha256(String.join("\n", String.valueOf(itemPublicId),
                String.valueOf(request.clientVersion()), safe(request.payload()),
                request.confidence() == null ? "" : request.confidence().name()));
    }

    static String saveAndExitRequestHash(PracticeSaveAndExitRequest request) {
        return sha256(String.join("\n", String.valueOf(request.clientVersion()),
                String.valueOf(request.itemPublicId()), safe(request.payload()),
                request.confidence() == null ? "" : request.confidence().name()));
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(PracticeConstants.PRACTICE_SHA256_UNAVAILABLE, ex);
        }
    }

    static String joinSorted(Collection<String> values) {
        return values == null ? "" : values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .distinct().sorted().collect(java.util.stream.Collectors.joining(","));
    }

    static Set<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(csv.split(","))
                .filter(value -> !value.isBlank()).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
