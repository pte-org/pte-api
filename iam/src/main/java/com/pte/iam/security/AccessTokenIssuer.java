package com.pte.iam.security;

import com.pte.common.security.SecurityClaims;
import com.pte.iam.constant.IamConstants;
import com.pte.iam.domain.User;
import com.pte.iam.domain.enums.Role;
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
 * {@code publicId}; carries {@code tenant_id} + {@code roles} claims that every
 * resource server reads locally (SecurityClaims contract).
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
                .issuer(IamConstants.TOKEN_ISSUER)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(IamConstants.ACCESS_TOKEN_TTL_SECONDS))
                .subject(user.getPublicId().toString())
                .claim(SecurityClaims.ROLES, roles);
        if (user.getTenantId() != null) {
            claims.claim(SecurityClaims.TENANT_ID, user.getTenantId().toString());
        }

        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(IamConstants.KEY_ID).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }
}
