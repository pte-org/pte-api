package com.pte.billing.internal.service.license;

import com.pte.billing.domain.LicenseCode;
import com.pte.billing.internal.constant.BillingConstants;
import com.pte.billing.internal.dto.response.LicenseIssueReceipt;
import com.pte.billing.internal.exception.license.LicenseCodeException;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

/** Generates a fresh bearer and stores it under the caller's idempotency key, retrying code collisions. */
class LicenseCodeIssuer {

    private static final int MAX_ISSUE_ATTEMPTS = 5;

    private final LicenseCodePersistenceService licenseCodePersistenceService;
    private final LicenseCodeGenerator licenseCodeGenerator;
    private final AuditLogService auditLogService;
    private final Clock clock;

    LicenseCodeIssuer(LicenseCodePersistenceService licenseCodePersistenceService,
            LicenseCodeGenerator licenseCodeGenerator, AuditLogService auditLogService, Clock clock) {
        this.licenseCodePersistenceService = licenseCodePersistenceService;
        this.licenseCodeGenerator = licenseCodeGenerator;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    LicenseIssueReceipt issue(UUID planPublicId, Instant codeExpiresAt, UUID key, CurrentUser caller) {
        if (planPublicId == null) {
            throw LicenseCodeAccessPolicy.invalid(BillingConstants.LICENSE_CODE_PLAN_REQUIRED);
        }
        if (key == null) {
            throw new LicenseCodeException(HttpStatus.BAD_REQUEST, BillingConstants.LICENSE_CODE_IDEMPOTENCY_KEY_REQUIRED);
        }
        if (codeExpiresAt != null && codeExpiresAt.getNano() % 1000 != 0) {
            throw new LicenseCodeException(HttpStatus.BAD_REQUEST, BillingConstants.LICENSE_CODE_EXPIRY_PRECISION_INVALID);
        }
        String fingerprint = fingerprint(planPublicId, codeExpiresAt);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        for (int attempt = 1; attempt <= MAX_ISSUE_ATTEMPTS; attempt++) {
            String code = licenseCodeGenerator.generate();
            LicenseCode licenseCode = LicenseCode.issue(code, planPublicId, caller.userId(), now, codeExpiresAt);
            try {
                LicenseIssueReceipt receipt = licenseCodePersistenceService.issue(licenseCode, key, fingerprint);
                if (auditLogService != null && !receipt.replayed()) {
                    auditLogService.record(caller, BillingConstants.LICENSE_CODE_AGGREGATE,
                            String.valueOf(receipt.publicId()),
                            BillingConstants.AUDIT_LICENSE_ISSUE, BillingConstants.LICENSE_CODE_ISSUE_AUDIT_SUMMARY);
                }
                return receipt;
            } catch (DataIntegrityViolationException ex) {
                if (!constraint(ex, BillingConstants.LICENSE_CODE_UNIQUE_CONSTRAINT)) {
                    throw new LicenseCodeException(HttpStatus.INTERNAL_SERVER_ERROR,
                            BillingConstants.LICENSE_CODE_ISSUE_FAILED, ex);
                }
            }
        }
        throw new LicenseCodeException(HttpStatus.SERVICE_UNAVAILABLE, BillingConstants.LICENSE_CODE_ISSUE_RETRYABLE);
    }

    private String fingerprint(UUID planId, Instant expiry) {
        try {
            String canonical = planId + "\n" + (expiry == null ? "null" : expiry.toString());
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private boolean constraint(Throwable failure, String name) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                    && name.equals(violation.getConstraintName())) return true;
        }
        return false;
    }
}
