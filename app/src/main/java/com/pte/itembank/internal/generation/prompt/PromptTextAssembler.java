package com.pte.itembank.internal.generation.prompt;

import com.pte.itembank.PromptDocument.Block;
import com.pte.itembank.PromptDocument.LevelRow;
import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Writes a stored prompt out as the text a model receives, and finds the {@code {name}} tokens it contains. */
public final class PromptTextAssembler {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{[a-zA-Z][a-zA-Z0-9]*}");
    private static final String TITLE_MARK = "# ";
    private static final String SECTION_MARK = "## ";
    private static final String BULLET_MARK = "- ";
    private static final String CODE_FENCE = "```";
    private static final String BLOCK_BREAK = "\n\n";
    private static final String LINE_BREAK = "\n";
    private static final String LEVEL_SEPARATOR = ": ";

    private PromptTextAssembler() {
    }

    public static String assemble(PromptContent content) {
        List<String> parts = new ArrayList<>();
        parts.add(TITLE_MARK + content.title());
        content.intro().forEach(block -> parts.add(render(block)));
        for (PromptContent.Section section : content.sections()) {
            parts.add(SECTION_MARK + section.title());
            section.blocks().forEach(block -> parts.add(render(block)));
        }
        return String.join(BLOCK_BREAK, parts) + LINE_BREAK;
    }

    /** The prompt without its title line: the text of a prompt whose title is only a label (a scoring prompt). */
    public static String assembleWithoutTitle(PromptContent content) {
        String full = assemble(content);
        return full.substring(full.indexOf(BLOCK_BREAK) + BLOCK_BREAK.length()).stripTrailing();
    }

    /** The distinct {@code {name}} tokens of {@code text}, in order of first use. */
    public static List<String> placeholdersOf(String text) {
        Set<String> tokens = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER.matcher(text);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return List.copyOf(tokens);
    }

    private static String render(Block block) {
        return switch (block.type()) {
            case TEXT -> labelled(block, block.text());
            case LIST -> labelled(block, block.items().stream().map(item -> BULLET_MARK + item)
                    .collect(Collectors.joining(LINE_BREAK)));
            case CODE -> labelled(block, CODE_FENCE + QuestionGenerationConstants.PROMPT_CODE_LANGUAGE + LINE_BREAK
                    + block.text() + LINE_BREAK + CODE_FENCE);
            case FIELDS -> labelled(block, CODE_FENCE + QuestionGenerationConstants.PROMPT_CODE_LANGUAGE
                    + LINE_BREAK + PromptFieldsJson.compose(block.items()) + LINE_BREAK + CODE_FENCE);
            case LEVELS -> labelled(block, block.levels().stream().map(PromptTextAssembler::levelLine)
                    .collect(Collectors.joining(LINE_BREAK)));
        };
    }

    private static String labelled(Block block, String body) {
        return block.label() == null || block.label().isBlank() ? body : block.label() + LINE_BREAK + body;
    }

    private static String levelLine(LevelRow row) {
        return BULLET_MARK + row.level() + LEVEL_SEPARATOR + row.text();
    }
}
