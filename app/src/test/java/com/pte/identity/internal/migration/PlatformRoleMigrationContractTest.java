package com.pte.identity.internal.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformRoleMigrationContractTest {

    @Test
    void roleCatalogKeepsTheLegacyAliasWhileAddingCanonicalPlatformRoles() throws IOException {
        String sql = resource("db/migration/V86__platform_role_catalog.sql");

        assertThat(sql)
                .contains("PLATFORM_MANAGER", "ACADEMIC_MANAGER", "ACADEMIC_STAFF", "PLATFORM_AUTHOR")
                .doesNotContain("DROP TABLE", "DELETE FROM audit_logs");
    }

    @Test
    void canonicalBackfillOnlyChangesLegacyRoleRowsAndDoesNotDeleteAuditHistory() throws IOException {
        String sql = resource("db/migration/V89__canonicalize_legacy_platform_author.sql");

        assertThat(sql)
                .contains("UPDATE user_roles")
                .contains("SET role = 'ACADEMIC_STAFF'")
                .contains("WHERE role = 'PLATFORM_AUTHOR'")
                .doesNotContain("DELETE FROM audit_logs", "DROP TABLE", "TRUNCATE");
    }

    @Test
    void platformUsernameIndexRebuildIsNarrowAndKeepsSoftDeletedRowsOutOfTheActiveKey() throws IOException {
        String sql = resource("db/migration/V90__rebuild_platform_username_uniqueness.sql");

        assertThat(sql)
                .contains("DROP INDEX IF EXISTS uk_users_platform_username")
                .contains("tenant_id IS NULL AND deleted = false")
                .doesNotContain("DROP TABLE", "DELETE FROM users", "TRUNCATE");
    }

    private static String resource(String path) throws IOException {
        try (InputStream input = PlatformRoleMigrationContractTest.class.getClassLoader()
                .getResourceAsStream(path)) {
            assertThat(input).as("migration resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
