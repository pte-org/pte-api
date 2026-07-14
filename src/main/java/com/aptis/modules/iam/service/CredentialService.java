package com.aptis.modules.iam.service;

import java.security.SecureRandom;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.aptis.modules.iam.interfaces.CredentialProvisioning;
import com.aptis.modules.iam.constant.IamApiConstants;
import com.aptis.modules.iam.constant.IamMessageConstants;

@Service
public class CredentialService implements CredentialProvisioning {

    private final PasswordEncoder passwordEncoder;
    private final PasswordEncoder importPasswordEncoder =
            new BCryptPasswordEncoder(IamApiConstants.IMPORT_BCRYPT_COST);
    private final ThreadLocal<SecureRandom> secureRandom =
            ThreadLocal.withInitial(SecureRandom::new);

    public CredentialService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public String hashPassword(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        return passwordEncoder.encode(rawPassword);
    }

    @Override
    public String hashImportedPassword(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        return importPasswordEncoder.encode(rawPassword);
    }

    @Override
    public String generateRandomCredential() {
        SecureRandom random = secureRandom.get();
        StringBuilder credential = new StringBuilder(IamApiConstants.GENERATED_PASSWORD_LENGTH);
        for (int index = 0; index < IamApiConstants.GENERATED_PASSWORD_LENGTH; index++) {
            credential.append(IamApiConstants.GENERATED_PASSWORD_CHARSET.charAt(
                    random.nextInt(IamApiConstants.GENERATED_PASSWORD_CHARSET.length())));
        }
        return credential.toString();
    }

    @Override
    public void validateCredentialLength(String credential) {
        if (credential == null || credential.length() < IamApiConstants.MIN_CREDENTIAL_LENGTH) {
            throw new IllegalArgumentException(IamMessageConstants.CREDENTIAL_TOO_SHORT);
        }
    }

    @Override
    public boolean validatePassword(String rawPassword, String hash) {
        if (rawPassword == null || hash == null) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, hash);
    }
}
