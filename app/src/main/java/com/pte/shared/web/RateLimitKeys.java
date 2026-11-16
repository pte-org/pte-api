package com.pte.shared.web;

import com.pte.shared.security.SecurityClaims;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

/**
 * Decides which buckets one request draws from, in the order they must be drawn. A signed-in caller draws from
 * their own bucket first and then from their tenant's, so a caller who is already over their own limit never
 * spends the tenant's allowance. A caller who is not signed in draws from the bucket of their client address.
 */
final class RateLimitKeys {

    static final String USER_PREFIX = "user:";
    static final String TENANT_PREFIX = "tenant:";
    static final String ADDRESS_PREFIX = "ip:";
    static final String ROUTE_SUFFIX = "|route:";
    static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    private RateLimitKeys() {
    }

    /** One bucket and the number of requests it allows each second. */
    record Limit(String key, int perSecond) {
    }

    static List<Limit> resolve(HttpServletRequest request, RateLimitSettings settings) {
        String uri = request.getRequestURI();
        Integer routeLimit = settings.routeLimits().get(uri);
        String suffix = routeLimit == null ? "" : ROUTE_SUFFIX + uri;

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth && hasText(jwtAuth.getToken().getSubject())) {
            Limit user = new Limit(USER_PREFIX + jwtAuth.getToken().getSubject() + suffix,
                    limitOr(routeLimit, settings.userPerSecond()));
            String tenantId = jwtAuth.getToken().getClaimAsString(SecurityClaims.TENANT_ID);
            if (!hasText(tenantId)) {
                return List.of(user);
            }
            return List.of(user,
                    new Limit(TENANT_PREFIX + tenantId + suffix, limitOr(routeLimit, settings.tenantPerSecond())));
        }
        String address = ClientAddress.resolve(request.getHeader(FORWARDED_FOR_HEADER), request.getRemoteAddr());
        return List.of(new Limit(ADDRESS_PREFIX + address + suffix,
                limitOr(routeLimit, settings.anonymousPerSecond())));
    }

    private static int limitOr(Integer routeLimit, int fallback) {
        return routeLimit == null ? fallback : routeLimit;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
