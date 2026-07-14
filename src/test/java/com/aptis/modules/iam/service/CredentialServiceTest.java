package com.aptis.modules.iam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class CredentialServiceTest {

    private CredentialService credentialService;

    @BeforeEach
    void setup() {
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        credentialService = new CredentialService(passwordEncoder);
    }

    @Test
    void hashPasswordReturnsBcryptHashDifferentFromInput() {
        String raw = "myPassword123";
        String hash = credentialService.hashPassword(raw);

        assertThat(hash).isNotEqualTo(raw);
        assertThat(hash).isNotEmpty();
    }

    @Test
    void hashPasswordProducesDifferentHashForSameInput() {
        String raw = "myPassword123";
        String hash1 = credentialService.hashPassword(raw);
        String hash2 = credentialService.hashPassword(raw);

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void hashPasswordNullThrows() {
        assertThrows(IllegalArgumentException.class, () -> credentialService.hashPassword(null));
    }

    @Test
    void hashPasswordBlankThrows() {
        assertThrows(IllegalArgumentException.class, () -> credentialService.hashPassword("   "));
    }

    @Test
    void generateRandomCredentialIsAtLeast8Chars() {
        String cred = credentialService.generateRandomCredential();

        assertThat(cred).isNotEmpty();
        assertThat(cred.length()).isGreaterThanOrEqualTo(8);
    }

    @Test
    void generateRandomCredentialIsUniquePerCall() {
        String cred1 = credentialService.generateRandomCredential();
        String cred2 = credentialService.generateRandomCredential();

        assertThat(cred1).isNotEqualTo(cred2);
    }

    @Test
    void validateCredentialLengthTooShortThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> credentialService.validateCredentialLength("short"));
    }

    @Test
    void validateCredentialLengthNullThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> credentialService.validateCredentialLength(null));
    }

    @Test
    void validateCredentialLengthValidDoesNotThrow() {
        credentialService.validateCredentialLength("validCred123");
    }

    @Test
    void validatePasswordMatchesReturnsTrue() {
        String raw = "myPassword123";
        String hash = credentialService.hashPassword(raw);

        assertTrue(credentialService.validatePassword(raw, hash));
    }

    @Test
    void validatePasswordMismatchReturnsFalse() {
        String raw = "myPassword123";
        String hash = credentialService.hashPassword(raw);

        assertFalse(credentialService.validatePassword("wrongPassword", hash));
    }

    @Test
    void validatePasswordNullInputsReturnFalseNotThrow() {
        assertFalse(credentialService.validatePassword(null, "somehash"));
        assertFalse(credentialService.validatePassword("raw", null));
    }
}
