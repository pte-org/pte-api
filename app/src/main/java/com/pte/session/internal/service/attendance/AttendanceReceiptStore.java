package com.pte.session.internal.service.attendance;

import com.pte.session.domain.ExamSession;
import com.pte.session.internal.constant.AttendanceConstants;
import com.pte.session.internal.exception.attendance.AttendanceIdempotencyConflictException;
import com.pte.shared.idempotency.IdempotencyDecision;
import com.pte.shared.idempotency.IdempotencyKeyService;
import com.pte.shared.idempotency.IdempotencyOutcome;
import com.pte.shared.idempotency.domain.IdempotencyScope;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Dedupe and receipt storage of attendance commands, kept in the shared idempotency table. A key is
 * {@code kind:session:actor:request}, so one actor's retry replays the stored receipt while a reused
 * request id with a different intent is rejected. Rows live until 90 days after the session closes.
 */
@Component
class AttendanceReceiptStore {

    private static final String SHA_256 = "SHA-256";

    private final IdempotencyKeyService idempotencyKeyService;
    private final JsonMapper jsonMapper;

    AttendanceReceiptStore(IdempotencyKeyService idempotencyKeyService, JsonMapper jsonMapper) {
        this.idempotencyKeyService = idempotencyKeyService;
        this.jsonMapper = jsonMapper;
    }

    /** Claims the key for a new command, or returns the stored receipt of an identical earlier command. */
    <T> Optional<T> replayOrBegin(AttendanceActionKind kind, ExamSession session, UUID actorPublicId,
            UUID requestPublicId, String intentFingerprint, Class<T> receiptType) {
        IdempotencyDecision decision = idempotencyKeyService.begin(IdempotencyScope.ATTENDANCE,
                key(kind, session.getPublicId(), actorPublicId, requestPublicId), session.getTenantId(),
                intentFingerprint, session.getClosesAt());
        if (decision.outcome() == IdempotencyOutcome.CONFLICT) {
            throw new AttendanceIdempotencyConflictException(
                    AttendanceConstants.MSG_REQUEST_ID_REUSED.formatted(requestPublicId));
        }
        if (decision.outcome() == IdempotencyOutcome.REPLAY) {
            return Optional.of(jsonMapper.readValue(decision.resultJson(), receiptType));
        }
        return Optional.empty();
    }

    void complete(AttendanceActionKind kind, ExamSession session, UUID actorPublicId, UUID requestPublicId,
            Object receipt) {
        idempotencyKeyService.complete(IdempotencyScope.ATTENDANCE,
                key(kind, session.getPublicId(), actorPublicId, requestPublicId), session.getTenantId(),
                jsonMapper.writeValueAsString(receipt));
    }

    <T> Optional<T> find(AttendanceActionKind kind, UUID tenantId, UUID sessionPublicId, UUID actorPublicId,
            UUID requestPublicId, Class<T> receiptType) {
        return idempotencyKeyService.findResult(IdempotencyScope.ATTENDANCE,
                        key(kind, sessionPublicId, actorPublicId, requestPublicId), tenantId)
                .map(json -> jsonMapper.readValue(json, receiptType));
    }

    String audit(Object state) {
        return jsonMapper.writeValueAsString(state);
    }

    <T> T readState(String json, Class<T> type) {
        return json == null ? null : jsonMapper.readValue(json, type);
    }

    /** Hex SHA-256 of the canonical command intent. */
    static String fingerprint(String canonicalIntent) {
        try {
            byte[] digest = MessageDigest.getInstance(SHA_256).digest(canonicalIntent.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(SHA_256, ex);
        }
    }

    private static String key(AttendanceActionKind kind, UUID sessionPublicId, UUID actorPublicId,
            UUID requestPublicId) {
        String separator = AttendanceConstants.KEY_SEPARATOR;
        return kind.keyPrefix() + separator + sessionPublicId + separator + actorPublicId + separator
                + requestPublicId;
    }
}
