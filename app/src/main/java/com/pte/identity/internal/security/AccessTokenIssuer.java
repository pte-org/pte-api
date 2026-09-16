package com.pte.identity.internal.security;

import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.internal.constant.IdentityConstants;
import com.pte.shared.security.SecurityClaims;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Signs a short-lived access JWT for a user. Subject is the user's stable
 * {@code publicId}; carries {@code tenant_id} + {@code roles} claims that
 * {@link com.pte.shared.security.CurrentUserContext} reads locally.
 */
@Component
public class AccessTokenIssuer {

    private final JwtEncoder jwtEncoder;

    public AccessTokenIssuer(JwtEncoder jwtEncoder) {
        this.jwtEncoder = jwtEncoder;
    }

    public String issue(User user) {
        Instant now = Instant.now();
        List<String> roles = user.getRoles().stream().map(Role::name).toList();

        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(IdentityConstants.TOKEN_ISSUER)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(IdentityConstants.ACCESS_TOKEN_TTL_SECONDS))
                .subject(user.getPublicId().toString())
                .claim(SecurityClaims.ROLES, roles);
        if (user.getTenantId() != null) {
            claims.claim(SecurityClaims.TENANT_ID, user.getTenantId().toString());
        }

        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(IdentityConstants.KEY_ID).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }
}
