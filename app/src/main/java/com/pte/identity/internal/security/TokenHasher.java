package com.pte.identity.internal.security;

import com.pte.identity.internal.constant.IdentityConstants;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Hashes refresh tokens before storage. The raw token is high-entropy random, so
 * a fast SHA-256 (not a slow password hash) is the right tool.
 */
@Component
public class TokenHasher {

    public String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(IdentityConstants.SHA256_UNAVAILABLE, ex);
        }
    }
}
