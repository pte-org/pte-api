package com.pte.session.internal.service;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.session.internal.repository.ExamSessionRepository;
import com.pte.tenancy.TenancyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * Generates the human-readable exam code {@code {TENANT}-{YYMMDD}-{RAND4}}
 * (e.g. {@code FPT-261010-K7QM}) that hosts share and students type instead
 * of the session UUID. Migration V72 backfills existing rows with the same
 * format — keep the two in sync.
 */
@Component
public class SessionCodeGenerator {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int RANDOM_PART_LENGTH = 4;
    private static final int MAX_TENANT_PART_LENGTH = 8;
    private static final int MAX_ATTEMPTS = 5;
    /** Tenants have no timezone field; the date part follows the Vietnam calendar day. */
    private static final ZoneId CODE_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyMMdd");

    private final TenancyService tenancyService;
    private final ExamSessionRepository sessionRepository;
    private final SecureRandom secureRandom;

    @Autowired
    public SessionCodeGenerator(TenancyService tenancyService, ExamSessionRepository sessionRepository) {
        this(tenancyService, sessionRepository, new SecureRandom());
    }

    SessionCodeGenerator(TenancyService tenancyService, ExamSessionRepository sessionRepository,
            SecureRandom secureRandom) {
        this.tenancyService = tenancyService;
        this.sessionRepository = sessionRepository;
        this.secureRandom = secureRandom;
    }

    /**
     * Pre-checks each candidate instead of catching the unique violation: a
     * violation would mark the caller's transaction rollback-only, and
     * {@code SessionLifecycleService} already maps integrity violations to the
     * overlap error. The DB UNIQUE constraint remains the final guard.
     */
    public String generate(UUID tenantId, Instant opensAt) {
        String prefix = tenantPart(tenancyService.getTenantCode(tenantId)) + "-"
                + DATE_FORMAT.format(opensAt.atZone(CODE_ZONE)) + "-";
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = prefix + randomPart();
            if (!sessionRepository.existsBySessionCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(SessionConstants.SESSION_CODE_GENERATION_FAILED);
    }

    private static String tenantPart(String tenantCode) {
        String normalized = tenantCode.replace("-", "").toUpperCase(Locale.ROOT);
        return normalized.length() <= MAX_TENANT_PART_LENGTH
                ? normalized
                : normalized.substring(0, MAX_TENANT_PART_LENGTH);
    }

    private String randomPart() {
        StringBuilder suffix = new StringBuilder(RANDOM_PART_LENGTH);
        for (int index = 0; index < RANDOM_PART_LENGTH; index++) {
            suffix.append(ALPHABET.charAt(secureRandom.nextInt(ALPHABET.length())));
        }
        return suffix.toString();
    }
}
