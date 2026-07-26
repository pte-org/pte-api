package com.pte.proctor.service;

import com.pte.proctor.domain.enums.ViolationType;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Computes the tamper-evident hash chain link for one {@link
 * com.pte.proctor.domain.ViolationEvent}. Deterministic given its inputs — a
 * chain is verified by recomputing every link from the stored rows and
 * comparing, not by trusting the stored {@code hash} column alone.
 */
@Component
public class HashChainService {

    private static final String ALGORITHM = "SHA-256";
    private static final String DELIMITER = "|";

    public String computeHash(UUID sessionPublicId, int sequenceNo, UUID attemptPublicId, ViolationType violationType,
                              String detail, Instant detectedAt, String prevHash) {
        String canonical = String.join(DELIMITER,
                sessionPublicId.toString(),
                String.valueOf(sequenceNo),
                attemptPublicId.toString(),
                violationType.name(),
                detail == null ? "" : detail,
                detectedAt.toString(),
                prevHash == null ? "" : prevHash);
        return sha256Hex(canonical);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
