package com.pte.billing.internal.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/** Generates readable, high-entropy bearer codes without ambiguous characters. */
@Component
public class LicenseCodeGenerator {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int RAW_LENGTH = 20;
    private static final int GROUP_LENGTH = 4;

    private final SecureRandom secureRandom;

    public LicenseCodeGenerator() {
        this(new SecureRandom());
    }

    LicenseCodeGenerator(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    public String generate() {
        StringBuilder code = new StringBuilder(RAW_LENGTH + RAW_LENGTH / GROUP_LENGTH - 1);
        for (int index = 0; index < RAW_LENGTH; index++) {
            if (index > 0 && index % GROUP_LENGTH == 0) {
                code.append('-');
            }
            code.append(ALPHABET.charAt(secureRandom.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
