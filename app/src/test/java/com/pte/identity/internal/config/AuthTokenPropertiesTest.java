package com.pte.identity.internal.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthTokenPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class);

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AuthTokenProperties.class)
    static class PropertiesConfig {
    }

    @Test
    void defaultsPreserveExistingLifetimes() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            AuthTokenProperties properties = context.getBean(AuthTokenProperties.class);
            assertThat(properties.getAccessTokenTtlSeconds()).isEqualTo(900);
            assertThat(properties.getRefreshTokenTtlSeconds()).isEqualTo(604_800);
        });
    }

    @Test
    void bindsConfiguredLifetimes() {
        runner.withPropertyValues("app.auth.tokens.access-token-ttl-seconds=1200",
                "app.auth.tokens.refresh-token-ttl-seconds=7200").run(context -> {
                    assertThat(context).hasNotFailed();
                    AuthTokenProperties properties = context.getBean(AuthTokenProperties.class);
                    assertThat(properties.getAccessTokenTtlSeconds()).isEqualTo(1200);
                    assertThat(properties.getRefreshTokenTtlSeconds()).isEqualTo(7200);
                });
    }

    @Test
    void environmentVariablesResolveThroughApplicationYaml() throws Exception {
        // Test resources override application.yml on the classpath; exercise the real deployment mapping.
        var yaml = new YamlPropertySourceLoader().load("application", new FileSystemResource("src/main/resources/application.yml"));
        runner.withInitializer(context -> {
            yaml.forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
            context.getEnvironment().getPropertySources().addFirst(new SystemEnvironmentPropertySource("test-env",
                    Map.of("AUTH_ACCESS_TOKEN_TTL_SECONDS", "1800", "AUTH_REFRESH_TOKEN_TTL_SECONDS", "86400")));
        }).run(context -> {
            assertThat(context).hasNotFailed();
            AuthTokenProperties properties = context.getBean(AuthTokenProperties.class);
            assertThat(properties.getAccessTokenTtlSeconds()).isEqualTo(1800);
            assertThat(properties.getRefreshTokenTtlSeconds()).isEqualTo(86400);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "invalid", "9223372036854775808"})
    void rejectsInvalidAccessLifetime(String ttl) {
        runner.withPropertyValues("app.auth.tokens.access-token-ttl-seconds=" + ttl)
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "900", "300", "invalid"})
    void rejectsInvalidRefreshLifetime(String ttl) {
        runner.withPropertyValues("app.auth.tokens.refresh-token-ttl-seconds=" + ttl)
                .run(context -> assertThat(context).hasFailed());
    }
}
