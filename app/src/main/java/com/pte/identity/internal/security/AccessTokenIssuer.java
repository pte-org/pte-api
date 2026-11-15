package com.pte.identity.internal.security;

import com.pte.identity.domain.User;
import com.pte.identity.internal.constant.IdentityConstants;
import com.pte.identity.internal.config.AuthTokenProperties;
import com.pte.shared.security.SecurityClaims;
import com.pte.shared.security.SecurityRoles;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Signs a short-lived access JWT for an account. The subject is the account's stable {@code publicId}. An account
 * that belongs to an organization carries it as {@code tenant_id}; platform and practice-only accounts have none.
 * The roles are the account's own. {@link com.pte.shared.security.CurrentUserContext} reads these claims locally.
 */
@Component
public class AccessTokenIssuer {

    private final JwtEncoder jwtEncoder;
    private final AuthTokenProperties tokenProperties;
    private final Clock clock;

    public AccessTokenIssuer(JwtEncoder jwtEncoder, AuthTokenProperties tokenProperties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.tokenProperties = tokenProperties;
        this.clock = clock;
    }

    public String issue(User user) {
        Instant now = clock.instant();
        List<String> roles = SecurityRoles.canonicalizeRoles(
                user.getRoles().stream().map(role -> role.name()).toList());
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(IdentityConstants.TOKEN_ISSUER)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(tokenProperties.getAccessTokenTtlSeconds()))
                .subject(user.getPublicId().toString())
                .claim(SecurityClaims.ROLES, roles);
        if (user.getOrganizationId() != null) {
            claims.claim(SecurityClaims.TENANT_ID, user.getOrganizationId().toString());
        }
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(IdentityConstants.KEY_ID).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }
}
