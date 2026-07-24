package com.pte.iam.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.pte.iam.security.RsaKeyProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.interfaces.RSAPublicKey;

/**
 * Wires the JWT signer (encoder) and iam's own validator (decoder) from the
 * RSA key pair. iam validates the tokens it issues in-process — it is both auth
 * server and a resource server for its own protected endpoints.
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
