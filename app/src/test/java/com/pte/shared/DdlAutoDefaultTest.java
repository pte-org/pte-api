package com.pte.shared;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * While the entity model is consolidated, local development lets Hibernate own the schema through the
 * {@code local-schema} profile. Every other environment must keep validating against Flyway migrations,
 * so the default configuration must not change.
 */
class DdlAutoDefaultTest {

    private static final Path RESOURCES = Path.of("src/main/resources");

    @Test
    void defaultConfigurationStillValidatesAgainstFlyway() throws IOException {
        String yaml = Files.readString(RESOURCES.resolve("application.yml"));

        assertThat(yaml).contains("ddl-auto: validate");
        assertThat(yaml).doesNotContain("ddl-auto: update");
    }

    @Test
    void localSchemaProfileOwnsTheSchemaWithHibernateAndSeedsPostgresObjects() throws IOException {
        String yaml = Files.readString(RESOURCES.resolve("application-local-schema.yml"));

        assertThat(yaml).contains("ddl-auto: update").contains("enabled: false");
        assertThat(Files.exists(RESOURCES.resolve("db/dev-seed/000-postgres-objects.sql"))).isTrue();
        assertThat(Files.exists(RESOURCES.resolve("db/dev-seed/010-reference-data.sql"))).isTrue();
    }

    @Test
    void localAccountSeedStoresOnlyABcryptHash() throws IOException {
        String sql = Files.readString(RESOURCES.resolve("db/dev-seed/020-local-accounts.sql"));

        assertThat(sql).contains("admin@test.local").contains("PLATFORM_ADMIN");
        assertThat(sql).containsPattern("\\$2[aby]\\$\\d{2}\\$");
        assertThat(sql).doesNotContain("Password123!");
    }
}
