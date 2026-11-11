package com.pte.itembank.internal.generation.prompt.version;

import com.pte.itembank.PromptDocument;
import com.pte.itembank.PromptDocument.Block;
import com.pte.itembank.internal.generation.constant.PromptVersionConstants;
import com.pte.itembank.internal.generation.domain.AiPromptKind;
import com.pte.itembank.internal.generation.exception.AiPromptException;
import com.pte.itembank.internal.generation.prompt.PromptContent;
import com.pte.itembank.internal.generation.prompt.PromptFieldsJson;
import com.pte.itembank.internal.generation.prompt.PictureSettings;
import com.pte.itembank.internal.generation.prompt.PromptTextAssembler;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Checks a prompt when it is saved and returns it cleaned (trimmed, with only the fields its block type uses), so
 * the same content always has the same hash. It guards the integrity of the prompt, not its wording: whether a
 * model still answers well is what the prompt test is for.
 */
@Component
public class PromptContentValidator {

    public PromptContent validated(AiPromptKind kind, PromptContent content) {
        return validated(kind, null, content);
    }

    /** Checks a prompt of a known key, which may also have to keep instructions the code depends on. */
    public PromptContent validated(AiPromptKind kind, String promptKey, PromptContent content) {
        List<String> problems = new ArrayList<>();
        String title = content.title() == null ? "" : content.title().strip();
        if (title.isEmpty() || title.length() > PromptVersionConstants.MAX_TITLE_LENGTH) {
            problems.add(PromptVersionConstants.PROBLEM_TITLE);
        }
        List<Block> intro = cleanBlocks(content.intro(), PromptVersionConstants.MAX_INTRO_BLOCKS, problems);
        List<PromptContent.Section> sections = cleanSections(content.sections(), problems);
        if (intro.isEmpty() && sections.isEmpty()) {
            problems.add(PromptVersionConstants.PROBLEM_EMPTY);
        }
        PromptContent clean = new PromptContent(title, intro, sections);
        if (problems.isEmpty()) {
            checkLengthAndTokens(kind, promptKey, clean, problems);
        }
        if (!problems.isEmpty()) {
            throw new AiPromptException(HttpStatus.BAD_REQUEST, PromptVersionConstants.CONTENT_INVALID,
                    String.join(" ", problems.stream().distinct().toList()));
        }
        return clean;
    }

    private static List<PromptContent.Section> cleanSections(List<PromptContent.Section> sections,
                                                             List<String> problems) {
        List<PromptContent.Section> clean = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        if (sections == null) {
            return clean;
        }
        if (sections.size() > PromptVersionConstants.MAX_SECTIONS) {
            problems.add(PromptVersionConstants.PROBLEM_TOO_MANY);
            return clean;
        }
        for (PromptContent.Section section : sections) {
            String id = section.id() == null ? "" : section.id().strip();
            String title = section.title() == null ? "" : section.title().strip();
            if (id.isEmpty() || id.length() > PromptVersionConstants.MAX_SECTION_ID_LENGTH || title.isEmpty()
                    || title.length() > PromptVersionConstants.MAX_TITLE_LENGTH || !ids.add(id)) {
                problems.add(PromptVersionConstants.PROBLEM_SECTION);
                continue;
            }
            List<Block> blocks = cleanBlocks(section.blocks(), PromptVersionConstants.MAX_BLOCKS_PER_SECTION,
                    problems);
            if (blocks.isEmpty()) {
                problems.add(PromptVersionConstants.PROBLEM_BLOCK);
            }
            clean.add(new PromptContent.Section(id, title, blocks));
        }
        return clean;
    }

    private static List<Block> cleanBlocks(List<Block> blocks, int limit, List<String> problems) {
        List<Block> clean = new ArrayList<>();
        if (blocks == null) {
            return clean;
        }
        if (blocks.size() > limit) {
            problems.add(PromptVersionConstants.PROBLEM_TOO_MANY);
            return clean;
        }
        for (Block block : blocks) {
            Block cleaned = cleanBlock(block, problems);
            if (cleaned != null) {
                clean.add(cleaned);
            }
        }
        return clean;
    }

    private static Block cleanBlock(Block block, List<String> problems) {
        if (block == null || block.type() == null) {
            problems.add(PromptVersionConstants.PROBLEM_BLOCK);
            return null;
        }
        String label = block.label() == null ? "" : block.label().strip();
        return switch (block.type()) {
            case TEXT, CODE -> cleanTextBlock(block, label, problems);
            case LIST -> cleanListBlock(block, label, problems);
            case FIELDS -> cleanFieldsBlock(block, label, problems);
            default -> {
                problems.add(PromptVersionConstants.PROBLEM_BLOCK);
                yield null;
            }
        };
    }

    private static Block cleanTextBlock(Block block, String label, List<String> problems) {
        String text = block.text() == null ? "" : block.text().strip();
        if (text.isEmpty()) {
            problems.add(PromptVersionConstants.PROBLEM_BLOCK);
            return null;
        }
        if (text.length() > PromptVersionConstants.MAX_BLOCK_TEXT_LENGTH) {
            problems.add(PromptVersionConstants.PROBLEM_TOO_LONG);
        }
        return block.type() == PromptDocument.BlockType.TEXT ? Block.text(label, text) : Block.code(label, text);
    }

    private static Block cleanListBlock(Block block, String label, List<String> problems) {
        List<String> items = block.items() == null ? List.of()
                : block.items().stream().filter(item -> item != null && !item.isBlank()).map(String::strip).toList();
        if (items.isEmpty() || items.size() > PromptVersionConstants.MAX_LIST_ITEMS) {
            problems.add(PromptVersionConstants.PROBLEM_BLOCK);
            return null;
        }
        if (items.stream().anyMatch(item -> item.length() > PromptVersionConstants.MAX_ITEM_LENGTH)) {
            problems.add(PromptVersionConstants.PROBLEM_TOO_LONG);
        }
        return Block.list(label, items);
    }

    /** Rows of a reply: each needs a field name, and no name may appear twice. */
    private static Block cleanFieldsBlock(Block block, String label, List<String> problems) {
        List<PromptFieldsJson.Row> rows = block.items() == null ? List.of()
                : block.items().stream().filter(item -> item != null && !item.isBlank())
                        .map(PromptFieldsJson::parseRow).toList();
        boolean named = !rows.isEmpty() && rows.stream().noneMatch(Objects::isNull);
        if (!named || rows.size() > PromptVersionConstants.MAX_LIST_ITEMS
                || rows.stream().map(PromptFieldsJson.Row::name).distinct().count() != rows.size()) {
            problems.add(PromptVersionConstants.PROBLEM_FIELDS);
            return null;
        }
        if (rows.stream().anyMatch(row -> row.value().length() > PromptVersionConstants.MAX_BLOCK_TEXT_LENGTH)) {
            problems.add(PromptVersionConstants.PROBLEM_TOO_LONG);
        }
        return Block.fields(label, rows.stream().map(row -> row.name() + ": " + row.value()).toList());
    }

    private static void checkLengthAndTokens(AiPromptKind kind, String promptKey, PromptContent content,
                                             List<String> problems) {
        String text = PromptTextAssembler.assemble(content);
        PromptRequirements.missingPhrases(kind, promptKey, text)
                .forEach(phrase -> problems.add(PromptVersionConstants.PROBLEM_MISSING_PHRASE + phrase + "."));
        problems.addAll(PictureSettings.problems(promptKey, content));
        if (text.length() > PromptVersionConstants.MAX_ASSEMBLED_LENGTH) {
            problems.add(PromptVersionConstants.PROBLEM_TOO_LONG);
        }
        List<String> tokens = PromptTextAssembler.placeholdersOf(text);
        if (kind == AiPromptKind.SHARED) {
            PromptVersionConstants.SHARED_REQUIRED_TOKENS.stream().filter(token -> !tokens.contains(token))
                    .forEach(token -> problems.add(PromptVersionConstants.PROBLEM_MISSING_TOKEN + token + "."));
        } else if (kind == AiPromptKind.GENERATOR) {
            tokens.forEach(token -> problems.add(PromptVersionConstants.PROBLEM_UNKNOWN_TOKEN + token + "."));
        }
    }
}
