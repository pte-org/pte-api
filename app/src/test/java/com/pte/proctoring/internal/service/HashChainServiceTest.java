package com.pte.proctoring.internal.service;

import com.pte.proctoring.domain.enums.ViolationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HashChainServiceTest {

    private HashChainService service;

    @BeforeEach
    void setUp() {
        service = new HashChainService();
    }

    @Test
    void computeHash_deterministicForSameInputs() {
        UUID sessionPublicId = UUID.randomUUID();
        int sequenceNo = 1;
        UUID attemptPublicId = UUID.randomUUID();
        ViolationType violationType = ViolationType.TAB_SWITCH;
        String detail = "user switched to another tab";
        Instant detectedAt = Instant.parse("2026-09-15T10:00:00Z");
        String prevHash = "abc123def456";

        String hash1 = service.computeHash(sessionPublicId, sequenceNo, attemptPublicId, violationType, detail, detectedAt, prevHash);
        String hash2 = service.computeHash(sessionPublicId, sequenceNo, attemptPublicId, violationType, detail, detectedAt, prevHash);

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void computeHash_changesWhenSequenceNoChanges() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID attemptPublicId = UUID.randomUUID();
        ViolationType violationType = ViolationType.TAB_SWITCH;
        String detail = "detail";
        Instant detectedAt = Instant.now();
        String prevHash = "abc123";

        String hash1 = service.computeHash(sessionPublicId, 1, attemptPublicId, violationType, detail, detectedAt, prevHash);
        String hash2 = service.computeHash(sessionPublicId, 2, attemptPublicId, violationType, detail, detectedAt, prevHash);

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void computeHash_changesWhenAttemptPublicIdChanges() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID attemptPublicId1 = UUID.randomUUID();
        UUID attemptPublicId2 = UUID.randomUUID();
        ViolationType violationType = ViolationType.TAB_SWITCH;
        String detail = "detail";
        Instant detectedAt = Instant.now();
        String prevHash = "abc123";
        int sequenceNo = 1;

        String hash1 = service.computeHash(sessionPublicId, sequenceNo, attemptPublicId1, violationType, detail, detectedAt, prevHash);
        String hash2 = service.computeHash(sessionPublicId, sequenceNo, attemptPublicId2, violationType, detail, detectedAt, prevHash);

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void computeHash_changesWhenViolationTypeChanges() {
        UUID sessionPublicId = UUID.randomUUID();
        int sequenceNo = 1;
        UUID attemptPublicId = UUID.randomUUID();
        String detail = "detail";
        Instant detectedAt = Instant.now();
        String prevHash = "abc123";

        String hash1 = service.computeHash(sessionPublicId, sequenceNo, attemptPublicId, ViolationType.TAB_SWITCH, detail, detectedAt, prevHash);
        String hash2 = service.computeHash(sessionPublicId, sequenceNo, attemptPublicId, ViolationType.MULTIPLE_FACES, detail, detectedAt, prevHash);

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void computeHash_changesWhenDetailChanges() {
        UUID sessionPublicId = UUID.randomUUID();
        int sequenceNo = 1;
        UUID attemptPublicId = UUID.randomUUID();
        ViolationType violationType = ViolationType.TAB_SWITCH;
        Instant detectedAt = Instant.now();
        String prevHash = "abc123";

        String hash1 = service.computeHash(sessionPublicId, sequenceNo, attemptPublicId, violationType, "detail1", detectedAt, prevHash);
        String hash2 = service.computeHash(sessionPublicId, sequenceNo, attemptPublicId, violationType, "detail2", detectedAt, prevHash);

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void computeHash_changesWhenDetectedAtChanges() {
        UUID sessionPublicId = UUID.randomUUID();
        int sequenceNo = 1;
        UUID attemptPublicId = UUID.randomUUID();
        ViolationType violationType = ViolationType.TAB_SWITCH;
        String detail = "detail";
        String prevHash = "abc123";
        Instant detectedAt1 = Instant.parse("2026-09-15T10:00:00Z");
        Instant detectedAt2 = Instant.parse("2026-09-15T10:00:01Z");

        String hash1 = service.computeHash(sessionPublicId, sequenceNo, attemptPublicId, violationType, detail, detectedAt1, prevHash);
        String hash2 = service.computeHash(sessionPublicId, sequenceNo, attemptPublicId, violationType, detail, detectedAt2, prevHash);

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void computeHash_changesWhenPrevHashChanges() {
        UUID sessionPublicId = UUID.randomUUID();
        int sequenceNo = 1;
        UUID attemptPublicId = UUID.randomUUID();
        ViolationType violationType = ViolationType.TAB_SWITCH;
        String detail = "detail";
        Instant detectedAt = Instant.now();

        String hash1 = service.computeHash(sessionPublicId, sequenceNo, attemptPublicId, violationType, detail, detectedAt, "abc123");
        String hash2 = service.computeHash(sessionPublicId, sequenceNo, attemptPublicId, violationType, detail, detectedAt, "def456");

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void computeHash_nullDetailTreatedAsEmpty() {
        UUID sessionPublicId = UUID.randomUUID();
        int sequenceNo = 1;
        UUID attemptPublicId = UUID.randomUUID();
        ViolationType violationType = ViolationType.TAB_SWITCH;
        Instant detectedAt = Instant.now();
        String prevHash = "abc123";

        // Null detail should be treated as empty string in the canonical string
        String hashWithNull = service.computeHash(sessionPublicId, sequenceNo, attemptPublicId, violationType, null, detectedAt, prevHash);
        String hashWithEmpty = service.computeHash(sessionPublicId, sequenceNo, attemptPublicId, violationType, "", detectedAt, prevHash);

        assertThat(hashWithNull).isEqualTo(hashWithEmpty);
    }

    @Test
    void computeHash_nullPrevHashTreatedAsEmpty() {
        UUID sessionPublicId = UUID.randomUUID();
        int sequenceNo = 1;
        UUID attemptPublicId = UUID.randomUUID();
        ViolationType violationType = ViolationType.TAB_SWITCH;
        String detail = "detail";
        Instant detectedAt = Instant.now();

        // Null prevHash should be treated as empty string in the canonical string
        String hashWithNull = service.computeHash(sessionPublicId, sequenceNo, attemptPublicId, violationType, detail, detectedAt, null);
        String hashWithEmpty = service.computeHash(sessionPublicId, sequenceNo, attemptPublicId, violationType, detail, detectedAt, "");

        assertThat(hashWithNull).isEqualTo(hashWithEmpty);
    }

    @Test
    void computeHash_producesValidHexString() {
        UUID sessionPublicId = UUID.randomUUID();
        int sequenceNo = 1;
        UUID attemptPublicId = UUID.randomUUID();
        ViolationType violationType = ViolationType.TAB_SWITCH;
        String detail = "detail";
        Instant detectedAt = Instant.now();
        String prevHash = "abc123";

        String hash = service.computeHash(sessionPublicId, sequenceNo, attemptPublicId, violationType, detail, detectedAt, prevHash);

        // SHA-256 produces 64 hex characters
        assertThat(hash).matches("^[a-f0-9]{64}$");
    }
}
