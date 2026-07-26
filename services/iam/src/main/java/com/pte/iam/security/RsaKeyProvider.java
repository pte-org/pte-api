package com.pte.iam.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.pte.iam.constant.IamConstants;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * Provides the RSA key pair iam signs JWTs with and publishes (public half) via
 * JWKS. Every other service validates tokens locally against this JWKS — no
 * per-request call to iam (ADR-002).
 *
 * <p>Dev generates an ephemeral key at startup (tokens invalidate on restart).
 * Production MUST load a stable key from Vault/env — TODO before release, gated
 * by the security review in Phase 9.
 */
@Component
public class RsaKeyProvider {

    private final RSAKey rsaKey;

    public RsaKeyProvider() {
        this.rsaKey = generateKey();
    }

    public RSAKey rsaKey() {
        return rsaKey;
    }

    public JWKSet publicJwkSet() {
        return new JWKSet(rsaKey.toPublicJWK());
    }

    private RSAKey generateKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                    .privateKey((RSAPrivateKey) pair.getPrivate())
                    .keyID(IamConstants.KEY_ID)
                    .build();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("RSA key generation failed", ex);
        }
    }
}
