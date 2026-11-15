package com.pte.itembank.internal.generation.prompt;

import com.pte.itembank.PromptDocument;
import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * How a Describe Image picture is made, edited on the picture prompt page: how many paint attempts an item gets, and
 * whether an item whose picture keeps failing becomes a chart at once or waits for the person to choose. The settings live in the prompt's
 * {@code image-settings} section, which the system reads and never sends to the model.
 *
 * @param maxAttempts     paint attempts (provider failures or reviewer refusals) before giving up on the picture
 * @param fallbackToChart true to make the item again as a chart at once; false (ASK) to fail it with
 *                        {@code GENERATION_IMAGE_FAILED}, so the person is asked whether to make it as a chart
 */
public record PictureSettings(int maxAttempts, boolean fallbackToChart) {

    public static final String SECTION_ID = "image-settings";
    public static final String MAX_ATTEMPTS = "maxImageAttempts";
    public static final String WHEN_FAILS = "whenImageFails";
    public static final String CHART = "CHART";
    public static final String ASK = "ASK";
    public static final int MIN_ATTEMPTS = 1;
    public static final int MAX_ATTEMPTS_LIMIT = 5;

    /** The settings of a picture prompt; a missing or unreadable value keeps its default. */
    public static PictureSettings from(Optional<PromptContent> content, PictureSettings defaults) {
        List<PromptFieldsJson.Row> rows = content.map(PictureSettings::rows).orElse(List.of());
        int attempts = rows.stream().filter(row -> MAX_ATTEMPTS.equals(row.name())).findFirst()
                .map(row -> parseAttempts(row.value())).orElse(defaults.maxAttempts());
        boolean toChart = rows.stream().filter(row -> WHEN_FAILS.equals(row.name())).findFirst()
                .map(row -> CHART.equals(clean(row.value()))).orElse(defaults.fallbackToChart());
        return new PictureSettings(attempts, toChart);
    }

    /** What is wrong with the settings of a picture prompt being saved; empty for any other prompt. */
    public static List<String> problems(String promptKey, PromptContent content) {
        List<String> problems = new ArrayList<>();
        if (!QuestionGenerationConstants.PROMPT_KEY_DESCRIBE_IMAGE_PICTURE.equals(promptKey)) {
            return problems;
        }
        for (PromptFieldsJson.Row row : rows(content)) {
            if (MAX_ATTEMPTS.equals(row.name()) && parseAttempts(row.value()) == null) {
                problems.add(QuestionGenerationConstants.PICTURE_ATTEMPTS_INVALID);
            }
            if (WHEN_FAILS.equals(row.name()) && !List.of(CHART, ASK).contains(clean(row.value()))) {
                problems.add(QuestionGenerationConstants.PICTURE_FALLBACK_INVALID);
            }
        }
        return problems;
    }

    /** The prompt without its settings section: what the writer model receives. */
    public static PromptContent withoutSettings(PromptContent content) {
        return new PromptContent(content.title(), content.intro(), content.sections().stream()
                .filter(section -> !SECTION_ID.equals(section.id())).toList());
    }

    private static List<PromptFieldsJson.Row> rows(PromptContent content) {
        return content.sections().stream().filter(section -> SECTION_ID.equals(section.id()))
                .flatMap(section -> section.blocks().stream())
                .filter(block -> block.type() == PromptDocument.BlockType.FIELDS)
                .flatMap(block -> block.items().stream()).map(PromptFieldsJson::parseRow)
                .filter(Objects::nonNull).toList();
    }

    private static Integer parseAttempts(String value) {
        try {
            int attempts = Integer.parseInt(clean(value));
            return attempts >= MIN_ATTEMPTS && attempts <= MAX_ATTEMPTS_LIMIT ? attempts : null;
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace("\"", "").strip().toUpperCase(Locale.ROOT);
    }
}
