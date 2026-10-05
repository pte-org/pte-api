package com.pte.itembank.internal.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

/** Rehearses V76 -> V77 with old writer behavior and representative legacy states. */
@EnabledIfSystemProperty(named = "lifecycle.test.db.url", matches = ".+")
class QuestionPublicationMigrationPostgresTest {
    @Test void legacyClassificationAndPublicationHistoryRemainFailClosedAndMonotonic() {
        String supplied = System.getProperty("lifecycle.test.db.url");
        if (!"jdbc:postgresql://127.0.0.1:55439/lifecycle_test?currentSchema=migration_clean".equals(supplied)) {
            throw new IllegalArgumentException("Only the dedicated local lifecycle test database is permitted");
        }
        String schema = "legacy_lifecycle_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:postgresql://127.0.0.1:55439/lifecycle_test";
        Flyway.configure().dataSource(url, "codex_lifecycle_test", "").schemas(schema)
                .locations("classpath:db/migration").target("76").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(url + "?currentSchema=" + schema, "codex_lifecycle_test", ""));
        for (String status : new String[] {"DRAFT", "ARCHIVED", "APPROVED"}) {
            jdbc.update("""
                insert into questions (public_id,created_at,updated_at,deleted,pte_task_type,visibility,status,title,
                  task_type_key,task_type_section,revision_group_public_id,revision_number,is_current,version)
                values (?,now(),now(),false,'READ_ALOUD','SHARED',?,?,'READ_ALOUD','SPEAKING',?,1,true,0)
                """, UUID.randomUUID(), status, status, UUID.randomUUID());
        }
        Flyway.configure().dataSource(url, "codex_lifecycle_test", "").schemas(schema)
                .locations("classpath:db/migration").load().migrate();
        assertThat(jdbc.queryForObject("select ever_published from questions where title='DRAFT'", Boolean.class)).isNull();
        assertThat(jdbc.queryForObject("select ever_published from questions where title='ARCHIVED'", Boolean.class)).isNull();
        assertThat(jdbc.queryForObject("select ever_published from questions where title='APPROVED'", Boolean.class)).isTrue();
        // Simulate an old application: it knows only status, not the new provenance column.
        jdbc.update("update questions set status='APPROVED' where title='DRAFT'");
        jdbc.update("update questions set status='ARCHIVED' where title='DRAFT'");
        jdbc.update("update questions set status='DRAFT', ever_published=false where title='DRAFT'");
        assertThat(jdbc.queryForObject("select ever_published from questions where title='DRAFT'", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from questions", Long.class)).isEqualTo(3);
    }
}
