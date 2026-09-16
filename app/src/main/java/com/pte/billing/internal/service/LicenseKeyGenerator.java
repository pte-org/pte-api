package com.pte.billing.internal.service;

import com.pte.billing.domain.Plan;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Locale;

/** Generates readable, cryptographically random license keys. */
@Component
public class LicenseKeyGenerator {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int RANDOM_PART_LENGTH = 6;
    private static final int MAX_PLAN_CODE_LENGTH = 12;

    private final SecureRandom secureRandom;

    public LicenseKeyGenerator() {
        this(new SecureRandom());
    }

    LicenseKeyGenerator(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    /**
     * Phase 3 has no separate plan-code field, so the readable prefix is a
     * bounded normalized form of the plan name. The random suffix excludes
     * 0/O/1/I/l to keep printed keys easy to read.
     */
    public String generate(Plan plan, Instant activatedAt) {
        StringBuilder suffix = new StringBuilder(RANDOM_PART_LENGTH);
        for (int index = 0; index < RANDOM_PART_LENGTH; index++) {
            suffix.append(ALPHABET.charAt(secureRandom.nextInt(ALPHABET.length())));
        }
        int year = ZonedDateTime.ofInstant(activatedAt, ZoneOffset.UTC).getYear();
        return "PTE-" + planCode(plan) + "-" + year + "-" + suffix;
    }

    private String planCode(Plan plan) {
        String normalized = plan.getName() == null
                ? "PLAN"
                : plan.getName().trim().replaceAll("[^A-Za-z0-9]+", "-")
                        .replaceAll("^-|-$", "").toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return "PLAN";
        }
        return normalized.length() <= MAX_PLAN_CODE_LENGTH
                ? normalized
                : normalized.substring(0, MAX_PLAN_CODE_LENGTH).replaceAll("-$", "");
    }
}
