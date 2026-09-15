package com.pte.shared.security;

import com.pte.shared.constant.SharedConstants;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Derives {@link CurrentUser} from the locally-validated JWT held in the
 * Spring Security context. No network call — the resource-server filter has
 * already verified the token signature.
 */
public final class CurrentUserContext {

    private CurrentUserContext() {
    }

    public static Optional<CurrentUser> current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken token)) {
            return Optional.empty();
        }
        return Optional.of(fromJwt(token.getToken()));
    }

    public static CurrentUser required() {
        return current().orElseThrow(() -> new IllegalStateException(SharedConstants.NO_AUTHENTICATED_PRINCIPAL));
    }

    private static CurrentUser fromJwt(Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        String tenantRaw = jwt.getClaimAsString(SecurityClaims.TENANT_ID);
        UUID tenantId = (tenantRaw == null || tenantRaw.isBlank()) ? null : UUID.fromString(tenantRaw);
        List<String> roles = jwt.getClaimAsStringList(SecurityClaims.ROLES);
        return new CurrentUser(userId, tenantId, roles == null ? List.of() : roles);
    }
}
