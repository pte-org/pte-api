package com.pte.identity.internal.config;

import org.junit.jupiter.api.Test;
import org.springframework.util.AntPathMatcher;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Someone who forgot their password cannot sign in, so exactly the two reset endpoints are reachable anonymously. */
class SecurityConfigPasswordResetPathTest {

    @SuppressWarnings("unchecked")
    private static List<String> publicPaths() throws Exception {
        Field field = SecurityConfig.class.getDeclaredField("PUBLIC_PATHS");
        field.setAccessible(true);
        return (List<String>) field.get(null);
    }

    @Test
    void theRequestAndConfirmEndpointsArePublic() throws Exception {
        AntPathMatcher matcher = new AntPathMatcher();

        assertThat(publicPaths()).anyMatch(p -> matcher.match(p, "/api/v1/auth/password-reset/request"));
        assertThat(publicPaths()).anyMatch(p -> matcher.match(p, "/api/v1/auth/password-reset/confirm"));
    }

    @Test
    void changePasswordStaysBehindAuthentication() throws Exception {
        AntPathMatcher matcher = new AntPathMatcher();

        assertThat(publicPaths()).noneMatch(p -> matcher.match(p, "/api/v1/auth/change-password"));
        assertThat(publicPaths()).noneMatch(p -> matcher.match(p, "/api/v1/auth/password-reset/other"));
    }
}
