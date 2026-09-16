package com.pte.identity.internal.controller;

import com.pte.identity.internal.security.RsaKeyProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Publishes the JSON Web Key Set (public key only). Kept even though nothing
 * in this monolith needs it yet (identity validates its own tokens in-process,
 * plan.md Phase 02) — Phase 11 cutover wiring the old gateway to the new app
 * will need exactly this endpoint. Returns the raw JWKS shape (not wrapped in
 * ApiResponse) so standard JWT clients/decoders can consume it directly.
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
