package com.aptis.modules.iam.interfaces;

public interface CredentialProvisioning {

    String hashPassword(String rawPassword);

    String hashImportedPassword(String rawPassword);

    String generateRandomCredential();

    void validateCredentialLength(String credential);

    boolean validatePassword(String rawPassword, String hash);
}
