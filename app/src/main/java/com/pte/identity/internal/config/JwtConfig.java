package com.pte.identity.internal.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.pte.identity.internal.security.RsaKeyProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.interfaces.RSAPublicKey;

/**
 * Wires the JWT signer (encoder) and identity's own validator (decoder) from
 * the RSA key pair. identity validates the tokens it issues in-process — it is
 * both auth server and resource server for its own protected endpoints. No
 * JWKS-over-HTTP round trip to any other service (there is no other service to
 * round-trip to for this module's own tokens).
 */
@Configuration
public class JwtConfig {

    @Bean
    public JwtEncoder jwtEncoder(RsaKeyProvider keyProvider) {
        JWKSource<SecurityContext> jwkSource =
                new ImmutableJWKSet<>(new JWKSet(keyProvider.rsaKey()));
        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    public JwtDecoder jwtDecoder(RsaKeyProvider keyProvider) {
        try {
            RSAPublicKey publicKey = keyProvider.rsaKey().toRSAPublicKey();
            return NimbusJwtDecoder.withPublicKey(publicKey).build();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to build JWT decoder", ex);
        }
    }
}
