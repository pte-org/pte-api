package com.pte.iam.controller;

import com.pte.iam.security.RsaKeyProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Publishes the JSON Web Key Set (public keys only). Every other service points
 * its resource-server {@code jwk-set-uri} here and validates tokens locally.
 * Returns the raw JWKS shape (not wrapped in ApiResponse) so standard JWT
 * clients/decoders can consume it directly.
 */
@RestController
@RequestMapping("/auth")
public class JwksController {

    private final RsaKeyProvider rsaKeyProvider;

    public JwksController(RsaKeyProvider rsaKeyProvider) {
        this.rsaKeyProvider = rsaKeyProvider;
    }

    @GetMapping("/jwks")
    public Map<String, Object> jwks() {
        return rsaKeyProvider.publicJwkSet().toJSONObject();
    }
}
