package com.pte.identity.internal.config;

import org.junit.jupiter.api.Test;
import org.springframework.util.AntPathMatcher;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The real filter chain is "permit the public list, anyRequest().authenticated()". The overview dashboards
 * must not sit on the public list, otherwise an anonymous caller would be answered by the controller instead of
 * receiving 401 from the chain. The list is private, so it is read reflectively inside its own module.
 */
class SecurityConfigDashboardPathTest {

    @SuppressWarnings("unchecked")
    private static List<String> publicPaths() throws Exception {
        Field field = SecurityConfig.class.getDeclaredField("PUBLIC_PATHS");
        field.setAccessible(true);
        return (List<String>) field.get(null);
    }

    @Test
    void neitherDashboardEndpointIsOnThePublicPathList() throws Exception {
        AntPathMatcher matcher = new AntPathMatcher();

        assertThat(publicPaths()).isNotEmpty();
        assertThat(publicPaths()).noneMatch(pattern -> matcher.match(pattern, "/api/v1/dashboard/admin"));
        assertThat(publicPaths()).noneMatch(pattern -> matcher.match(pattern, "/api/v1/dashboard/host"));
    }

    @Test
    void noPublicPatternIsABroadWildcardThatCouldSwallowTheDashboardPaths() throws Exception {
        assertThat(publicPaths()).noneMatch(pattern -> pattern.equals("/**") || pattern.equals("/api/**")
                || pattern.equals("/api/v1/**") || pattern.startsWith("/api/v1/dashboard"));
    }
}
