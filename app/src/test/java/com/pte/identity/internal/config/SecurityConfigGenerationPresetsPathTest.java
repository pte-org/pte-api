package com.pte.identity.internal.config;

import org.junit.jupiter.api.Test;
import org.springframework.util.AntPathMatcher;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The generation presets read must stay behind authentication (anonymous gets 401 from the chain). */
class SecurityConfigGenerationPresetsPathTest {

    @SuppressWarnings("unchecked")
    @Test
    void thePresetsPathIsNotPublic() throws Exception {
        Field field = SecurityConfig.class.getDeclaredField("PUBLIC_PATHS");
        field.setAccessible(true);
        List<String> publicPaths = (List<String>) field.get(null);
        AntPathMatcher matcher = new AntPathMatcher();

        assertThat(publicPaths).noneMatch(pattern -> matcher.match(pattern, "/api/v1/question-generation/presets"));
    }
}
