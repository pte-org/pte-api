package com.pte.notification;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationMigrationVersionTest {
    private static final Pattern MIGRATION_NAME = Pattern.compile("V(\\d+)__.+\\.sql");

    @Test
    void supportAndNotificationMigrationsHaveOneOrderedVersion() throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:db/migration/V*.sql");

        Map<Integer, List<String>> namesByVersion = Arrays.stream(resources)
                .map(Resource::getFilename)
                .filter(name -> name != null)
                .collect(Collectors.groupingBy(NotificationMigrationVersionTest::version,
                        Collectors.mapping(name -> name, Collectors.toList())));

        assertThat(namesByVersion.get(71)).containsExactly("V71__support_ticket.sql");
        assertThat(namesByVersion.get(72)).containsExactly("V72__notification_inbox_foundation.sql");
        assertThat(namesByVersion.get(73)).containsExactly("V73__notification_inbox_snapshots.sql");
        assertThat(namesByVersion.get(74)).containsExactly("V74__notification_announcement_query_indexes.sql");
        assertThat(namesByVersion.get(75)).containsExactly("V75__session_grading_cohort.sql");
        assertThat(namesByVersion.values()).allSatisfy(names -> assertThat(names).hasSize(1));
    }

    private static int version(String filename) {
        Matcher matcher = MIGRATION_NAME.matcher(filename);
        assertThat(matcher.matches()).as("valid Flyway filename: %s", filename).isTrue();
        return Integer.parseInt(matcher.group(1));
    }
}
