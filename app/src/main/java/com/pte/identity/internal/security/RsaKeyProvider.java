package com.pte.identity.internal.security;

import com.nimbusds.jose.jwk.RSAKey;
import com.pte.identity.internal.constant.IdentityConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

/**
 * Provides the RSA key pair identity signs and verifies JWTs with. No longer
 * publishes a JWKS (gateway-removal): that endpoint existed only for
 * gateway's remote JWKS fetch, and app has validated its own tokens
 * in-process since plan.md Phase 02 — nothing else ever consumed it.
 *
 * <p>Ported from {@code services/iam}'s {@code RsaKeyProvider}, which generated
 * an ephemeral key pair on every startup — its own javadoc flagged this as a
 * "TODO before release." Every restart invalidated every issued token platform-
 * wide. Fixed here rather than carried forward as debt (plan.md Phase 02 Design
 * Constraints): {@code IDENTITY_RSA_PRIVATE_KEY_PEM}, if set, is loaded as a
 * PKCS#8 PEM-encoded RSA private key; the public key and JWK are derived from
 * it. Empty/unset falls back to an ephemeral key (fine for a first local run)
 * with a warning — never silently ephemeral in a way that looks configured.
 *
 * <p>Generate a compatible key: {@code openssl genpkey -algorithm RSA
 * -pkeyopt rsa_keygen_bits:2048 -out identity-key.pem} — the default output is
 * already PKCS#8. Paste the file contents (including the BEGIN/END lines) into
 * the env var.
 */
@Component
public class RsaKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(RsaKeyProvider.class);

    private final RSAKey rsaKey;

    public RsaKeyProvider(@Value("${identity.rsa-private-key-pem:}") String pem) {
        this.rsaKey = pem.isBlank() ? generateEphemeralKey() : loadFromPem(pem);
    }

    public RSAKey rsaKey() {
        return rsaKey;
    }

    private RSAKey generateEphemeralKey() {
        log.warn("identity.rsa-private-key-pem is not set; generating an ephemeral RSA key. Every restart "
                + "invalidates every previously issued token. Set IDENTITY_RSA_PRIVATE_KEY_PEM before any "
                + "shared or production environment.");
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                    .privateKey((RSAPrivateKey) pair.getPrivate())
                    .keyID(IdentityConstants.KEY_ID)
                    .build();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(IdentityConstants.RSA_KEY_GENERATION_FAILED, ex);
        }
    }

    private RSAKey loadFromPem(String pem) {
        try {
            String base64 = pem
                    .replaceAll("-----BEGIN (.*)-----", "")
                    .replaceAll("-----END (.*)-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(base64);
            KeyFactory factory = KeyFactory.getInstance("RSA");
            RSAPrivateCrtKey privateKey =
                    (RSAPrivateCrtKey) factory.generatePrivate(new PKCS8EncodedKeySpec(der));
            RSAPublicKeySpec publicSpec =
                    new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent());
            RSAPublicKey publicKey = (RSAPublicKey) factory.generatePublic(publicSpec);
            return new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyID(IdentityConstants.KEY_ID)
                    .build();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | ClassCastException ex) {
            throw new IllegalStateException(IdentityConstants.INVALID_RSA_PRIVATE_KEY, ex);
        }
    }
}
