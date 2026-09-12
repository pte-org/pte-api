package com.pte.scoring.service;

import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that scoring consumes the same versioned Listening payload fixture
 * as pte-doc and pte-app. The fixture is vendored because the repositories do
 * not share a CI pipeline; a missing resource or version mismatch must fail
 * locally instead of being silently skipped. The test cannot detect a stale
 * copy whose content and version were both edited consistently, so contract
 * changes still require updating pte-doc first, then both vendored copies.
 */
class ListeningPayloadFixtureSchemaTest {

    private static final int EXPECTED_CONTRACT_VERSION = 1;

    private static final Set<String> EXPECTED_TASK_TYPES = Set.of(
            "SUMMARIZE_SPOKEN_TEXT",
            "WRITE_FROM_DICTATION",
            "MC_LISTENING_MULTIPLE",
            "HIGHLIGHT_CORRECT_SUMMARY",
            "SELECT_MISSING_WORD",
            "FILL_BLANKS_LISTENING",
            "HIGHLIGHT_INCORRECT_WORDS",
            "MC_LISTENING_SINGLE");

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void fixture_hasExpectedVersionSchemaAndExactlyEightListeningTypes() throws IOException {
        Map<String, Object> root = jsonMapper.readValue(fixtureJson(), new TypeReference<>() {
        });

        assertThat(root)
                .as("vendored Listening payload fixture must contain its top-level schema")
                .containsKeys("contractVersion", "lastUpdated", "fixtures");
        assertThat(root.get("contractVersion"))
                .as("fixture contractVersion must match the test constant; sync all three copies intentionally")
                .isEqualTo(EXPECTED_CONTRACT_VERSION);

        assertThat(root.get("fixtures")).isInstanceOf(List.class);
        List<?> entries = (List<?>) root.get("fixtures");
        assertThat(entries).hasSize(EXPECTED_TASK_TYPES.size());

        Set<String> taskTypes = new HashSet<>();
        for (Object rawEntry : entries) {
            assertThat(rawEntry).isInstanceOf(Map.class);
            Map<?, ?> entry = (Map<?, ?>) rawEntry;
            assertThat(entry.containsKey("taskType")).isTrue();
            assertThat(entry.containsKey("optionsJson")).isTrue();
            assertThat(entry.containsKey("payload")).isTrue();
            assertThat(entry.containsKey("description")).isTrue();
            assertThat(entry.get("taskType")).isInstanceOf(String.class);
            assertThat(entry.get("payload"))
                    .as("payload is an opaque wire string, never a nested JSON object")
                    .isInstanceOf(String.class);
            assertThat(entry.get("description")).isInstanceOf(String.class);
            assertThat(entry.get("optionsJson") == null || entry.get("optionsJson") instanceof String)
                    .as("optionsJson must be a JSON string or null")
                    .isTrue();
            taskTypes.add((String) entry.get("taskType"));
        }

        assertThat(taskTypes).containsExactlyInAnyOrderElementsOf(EXPECTED_TASK_TYPES);
    }

    private String fixtureJson() {
        try (InputStream stream = getClass().getResourceAsStream(
                "/fixtures/listening-payload-contract.json")) {
            if (stream == null) {
                throw new IllegalStateException(
                        "Vendored Listening payload fixture is missing from scoring test resources: "
                                + "/fixtures/listening-payload-contract.json");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not read vendored Listening payload fixture", ex);
        }
    }
}
