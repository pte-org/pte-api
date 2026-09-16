package com.pte.scoretemplate.internal.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parses {@code V14__score_template.sql} straight off the classpath (no
 * Postgres/Testcontainers in this codebase — see phase-01 Risks) and checks
 * every one of the 22 seeded rows against the APEUni V5 table in spec.md.
 * This is the test that directly satisfies spec.md's first success
 * criterion: "Template V5 seed: 22 item, mọi giá trị khớp bảng trên".
 */
class ScoreTemplateSeedMigrationTest {

    private record ExpectedRow(String taskType, String section, int sequence, int min, int max, int prep, int resp,
                                String timingMode, String scoringMethod, BigDecimal overall, BigDecimal speaking,
                                BigDecimal writing, BigDecimal reading, BigDecimal listening) {
    }

    private static final List<ExpectedRow> EXPECTED = List.of(
            row("READ_ALOUD", "SPEAKING", 1, 6, 7, 35, 40, "FIXED", "AI_SPEECH", "4", "9", "0", "0", "0"),
            row("REPEAT_SENTENCE", "SPEAKING", 2, 10, 12, 3, 15, "FIXED", "AI_SPEECH", "7", "16", "0", "0", "17"),
            row("DESCRIBE_IMAGE", "SPEAKING", 3, 5, 6, 25, 40, "FIXED", "AI_SPEECH", "15", "31", "0", "0", "0"),
            row("RE_TELL_LECTURE", "SPEAKING", 4, 2, 3, 10, 40, "FIXED", "AI_SPEECH", "6", "13", "0", "0", "13"),
            row("ANSWER_SHORT_QUESTION", "SPEAKING", 5, 5, 6, 3, 10, "FIXED", "AI_SPEECH", "2", "0", "0", "0", "4"),
            row("SUMMARIZE_GROUP_DISCUSSION", "SPEAKING", 6, 2, 3, 10, 120, "FIXED", "AI_SPEECH", "9", "19", "0", "0", "20"),
            row("RESPOND_TO_A_SITUATION", "SPEAKING", 7, 2, 3, 10, 40, "FIXED", "AI_SPEECH", "6", "13", "0", "0", "0"),
            row("SUMMARIZE_WRITTEN_TEXT", "WRITING", 8, 2, 2, 0, 600, "FIXED", "AI_TEXT", "7", "0", "28", "23", "0"),
            row("WRITE_ESSAY", "WRITING", 9, 1, 1, 0, 1200, "FIXED", "AI_TEXT", "7", "0", "31", "0", "0"),
            row("FILL_BLANKS_READING_WRITING", "READING", 10, 5, 6, 0, 90, "RECOMMENDED", "OBJECTIVE", "7", "0", "0", "25", "0"),
            row("MC_READING_MULTIPLE", "READING", 11, 2, 3, 0, 90, "RECOMMENDED", "OBJECTIVE", "1", "0", "0", "5", "0"),
            row("RE_ORDER_PARAGRAPHS", "READING", 12, 2, 3, 0, 75, "RECOMMENDED", "OBJECTIVE", "3", "0", "0", "9", "0"),
            row("FILL_BLANKS_READING", "READING", 13, 4, 5, 0, 90, "RECOMMENDED", "OBJECTIVE", "6", "0", "0", "20", "0"),
            row("MC_READING_SINGLE", "READING", 14, 2, 3, 0, 60, "RECOMMENDED", "OBJECTIVE", "0.5", "0", "0", "3", "0"),
            row("SUMMARIZE_SPOKEN_TEXT", "LISTENING", 15, 1, 1, 0, 600, "FIXED", "AI_TEXT", "4", "0", "18", "0", "10"),
            row("MC_LISTENING_MULTIPLE", "LISTENING", 16, 2, 3, 0, 20, "RECOMMENDED", "OBJECTIVE", "1", "0", "0", "0", "3"),
            row("FILL_BLANKS_LISTENING", "LISTENING", 17, 2, 3, 0, 60, "RECOMMENDED", "OBJECTIVE", "3", "0", "0", "0", "8"),
            row("HIGHLIGHT_CORRECT_SUMMARY", "LISTENING", 18, 2, 3, 0, 20, "RECOMMENDED", "OBJECTIVE", "0.5", "0", "0", "3", "2"),
            row("MC_LISTENING_SINGLE", "LISTENING", 19, 2, 3, 0, 20, "RECOMMENDED", "OBJECTIVE", "0.5", "0", "0", "0", "2"),
            row("SELECT_MISSING_WORD", "LISTENING", 20, 1, 2, 0, 20, "RECOMMENDED", "OBJECTIVE", "1", "0", "0", "0", "1"),
            row("HIGHLIGHT_INCORRECT_WORDS", "LISTENING", 21, 2, 3, 0, 30, "RECOMMENDED", "OBJECTIVE", "4", "0", "0", "13", "8"),
            row("WRITE_FROM_DICTATION", "LISTENING", 22, 3, 4, 0, 120, "FIXED", "OBJECTIVE", "5", "0", "23", "0", "13"));

    private static ExpectedRow row(String taskType, String section, int sequence, int min, int max, int prep, int resp,
                                    String timingMode, String scoringMethod, String overall, String speaking,
                                    String writing, String reading, String listening) {
        return new ExpectedRow(taskType, section, sequence, min, max, prep, resp, timingMode, scoringMethod,
                new BigDecimal(overall), new BigDecimal(speaking), new BigDecimal(writing), new BigDecimal(reading),
                new BigDecimal(listening));
    }

    /**
     * Matches one {@code score_template_items} VALUES tuple, skipping the
     * first 5 fixed columns (public_id/created_at/updated_at/deleted/template_id)
     * that {@code gen_random_uuid()}/{@code now()} fill at insert time.
     */
    private static final Pattern ROW_PATTERN = Pattern.compile(
            "gen_random_uuid\\(\\), now\\(\\), now\\(\\), FALSE, 1, "
                    + "'([A-Z_]+)',\\s*'([A-Z_]+)',\\s*(\\d+),\\s*(\\d+),\\s*(\\d+),\\s*(\\d+),\\s*(\\d+),\\s*"
                    + "'([A-Z_]+)',\\s*'([A-Z_]+)',\\s*([\\d.]+),\\s*([\\d.]+),\\s*([\\d.]+),\\s*([\\d.]+),\\s*([\\d.]+)\\)");

    private List<ExpectedRow> parseSeedMigration() {
        String sql = readMigrationFile();
        Matcher matcher = ROW_PATTERN.matcher(sql);
        List<ExpectedRow> parsed = new ArrayList<>();
        while (matcher.find()) {
            parsed.add(new ExpectedRow(
                    matcher.group(1), matcher.group(2), Integer.parseInt(matcher.group(3)),
                    Integer.parseInt(matcher.group(4)), Integer.parseInt(matcher.group(5)),
                    Integer.parseInt(matcher.group(6)), Integer.parseInt(matcher.group(7)),
                    matcher.group(8), matcher.group(9),
                    new BigDecimal(matcher.group(10)), new BigDecimal(matcher.group(11)),
                    new BigDecimal(matcher.group(12)), new BigDecimal(matcher.group(13)),
                    new BigDecimal(matcher.group(14))));
        }
        return parsed;
    }

    private String readMigrationFile() {
        try (InputStream in = getClass().getResourceAsStream("/db/migration/V14__score_template.sql")) {
            if (in == null) {
                throw new IllegalStateException("V14__score_template.sql not found on classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Test
    void seedHasExactly22Items() {
        assertThat(parseSeedMigration()).hasSize(22);
    }

    @Test
    void everySeededItem_matchesTheApeUniV5Table_cellByCell() {
        List<ExpectedRow> actual = parseSeedMigration();

        assertThat(actual).containsExactlyElementsOf(EXPECTED);
    }

    @Test
    void templateRow_isSeededActiveAsApeuniV5() {
        String sql = readMigrationFile();

        assertThat(sql).contains("'APEUNI_V5', 1,").contains("'ACTIVE'");
    }
}
