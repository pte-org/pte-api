package com.pte.attempt.internal.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Set;

/**
 * Holds attempt's own RSA-2048 keypair for answer-submission encryption
 * (STRICT {@code answerIntegrityLevel}) — deliberately separate from
 * identity's RSA key provider (JWT signing keys, different purpose/lifecycle)
 * so attempt never needs a call to identity for key material. Outside
 * dev/local profiles, missing PEM config fails fast at startup instead of
 * silently generating an ephemeral keypair that would invalidate every
 * STRICT-pinned attempt's public key on the next restart.
 */
@Component
public class EncryptionKeyProvider {

    private static final Set<String> EPHEMERAL_ALLOWED_PROFILES = Set.of("dev", "local");
    private static final int RSA_KEY_SIZE_BITS = 2048;

    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    public EncryptionKeyProvider(
            @Value("${attempt.encryption.private-key-pem:}") String privateKeyPem,
            @Value("${attempt.encryption.public-key-pem:}") String publicKeyPem,
            @Value("${spring.profiles.active:prod}") String activeProfile) {
        boolean privateMissing = isBlank(privateKeyPem);
        boolean publicMissing = isBlank(publicKeyPem);

        if (privateMissing || publicMissing) {
            if (!isEphemeralAllowed(activeProfile)) {
                throw new IllegalStateException("attempt encryption keypair is not configured "
                        + "(attempt.encryption.private-key-pem / public-key-pem) and active profile '"
                        + activeProfile + "' is not dev/local — refusing to start with an ephemeral keypair, "
                        + "since a restart would invalidate every STRICT-pinned attempt's public key.");
            }
            KeyPair ephemeral = generateEphemeralKeyPair();
            this.privateKey = ephemeral.getPrivate();
            this.publicKey = ephemeral.getPublic();
            return;
        }

        this.privateKey = parsePrivateKey(privateKeyPem);
        this.publicKey = parsePublicKey(publicKeyPem);
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public String getPublicKeyBase64() {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    private boolean isEphemeralAllowed(String activeProfile) {
        return activeProfile != null && EPHEMERAL_ALLOWED_PROFILES.contains(activeProfile.toLowerCase());
    }

    private KeyPair generateEphemeralKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(RSA_KEY_SIZE_BITS);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Failed to generate ephemeral RSA encryption keypair", ex);
        }
    }

    private PrivateKey parsePrivateKey(String pem) {
        try {
            byte[] der = decodePem(pem, "PRIVATE KEY");
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new IllegalStateException("Failed to parse attempt encryption private key PEM", ex);
        }
    }

    private PublicKey parsePublicKey(String pem) {
        try {
            byte[] der = decodePem(pem, "PUBLIC KEY");
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePublic(new X509EncodedKeySpec(der));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new IllegalStateException("Failed to parse attempt encryption public key PEM", ex);
        }
    }

    /** Strips PEM headers/footers and all whitespace variants (\r\n, \n, spaces) before Base64-decoding. */
    private byte[] decodePem(String pem, String label) {
        String normalized = pem
                .replace("-----BEGIN " + label + "-----", "")
                .replace("-----END " + label + "-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(normalized);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
