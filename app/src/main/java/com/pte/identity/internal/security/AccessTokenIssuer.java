package com.pte.identity.internal.security;

import com.pte.identity.domain.OrganizationMember;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.internal.constant.IdentityConstants;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Signs a short-lived access JWT for an account. The subject is the account's stable {@code publicId}. When the
 * account signs in to an organization the token carries that organization as {@code tenant_id} and the roles of the
 * membership (plus any platform role the account itself holds); an account without an organization carries only its
 * own roles. {@link com.pte.shared.security.CurrentUserContext} reads these claims locally.
 */
@Component
public class AccessTokenIssuer {

    private final JwtEncoder jwtEncoder;
    private final Clock clock;

    public AccessTokenIssuer(JwtEncoder jwtEncoder, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.clock = clock;
    }

    /** @param member the membership chosen at login, or null for a platform or practice-only account */
    public String issue(User user, OrganizationMember member) {
        Instant now = clock.instant();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(IdentityConstants.TOKEN_ISSUER)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(IdentityConstants.ACCESS_TOKEN_TTL_SECONDS))
                .subject(user.getPublicId().toString())
                .claim(SecurityClaims.ROLES, rolesFor(user, member));
        if (member != null) {
            claims.claim(SecurityClaims.TENANT_ID, member.getOrganizationId().toString());
        }
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(IdentityConstants.KEY_ID).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    private List<String> rolesFor(User user, OrganizationMember member) {
        Set<Role> roles = new LinkedHashSet<>();
        if (member == null) {
            roles.addAll(user.getRoles());
        } else {
            roles.addAll(member.getRoles());
            user.getRoles().stream().filter(role -> SecurityRoles.isPlatformRole(role.name())).forEach(roles::add);
        }
        return SecurityRoles.canonicalizeRoles(roles.stream().map(Role::name).toList());
    }
}
