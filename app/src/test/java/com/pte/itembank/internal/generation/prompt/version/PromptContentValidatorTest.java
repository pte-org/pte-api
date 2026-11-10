package com.pte.itembank.internal.generation.prompt.version;

import com.pte.itembank.PromptDocument;
import com.pte.itembank.PromptDocument.Block;
import com.pte.itembank.internal.generation.constant.PromptVersionConstants;
import com.pte.itembank.internal.generation.domain.AiPromptKind;
import com.pte.itembank.internal.generation.exception.AiPromptException;
import com.pte.itembank.internal.generation.prompt.PromptContent;
import com.pte.itembank.internal.generation.prompt.PromptTestRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptContentValidatorTest {

    private final PromptContentValidator validator = new PromptContentValidator();

    private static PromptContent shipped(AiPromptKind kind, String key) {
        return PromptTestRegistry.codec().read(PromptTestRegistry.find(kind, key).orElseThrow().getContent());
    }

    @Test
    void everyShippedPromptIsValidAndUnchangedByCleaning() {
        PromptTestRegistry.seededVersions().forEach(version -> {
            PromptContent content = PromptTestRegistry.codec().read(version.getContent());
            assertThat(validator.validated(version.getKind(), content)).isEqualTo(content);
        });
    }

    @Test
    void textIsTrimmedAndUnusedFieldsDropped() {
        PromptContent messy = new PromptContent("  Title ", List.of(new Block(PromptDocument.BlockType.TEXT, " label ",
                "  body \n", List.of("x"), List.of(), false)), List.of());

        PromptContent clean = validator.validated(AiPromptKind.GENERATOR, messy);

        assertThat(clean.title()).isEqualTo("Title");
        assertThat(clean.intro()).containsExactly(Block.text("label", "body"));
    }

    @Test
    void theSharedBlockMustKeepEveryToken() {
        PromptContent shared = shipped(AiPromptKind.SHARED, "COMMON_GENERATOR");
        PromptContent withoutCefr = new PromptContent(shared.title(), List.of(Block.text("", "No tokens at all.")),
                List.of());

        assertThatThrownBy(() -> validator.validated(AiPromptKind.SHARED, withoutCefr))
                .isInstanceOfSatisfying(AiPromptException.class, failure -> {
                    assertThat(failure.getCode()).isEqualTo(PromptVersionConstants.CONTENT_INVALID);
                    assertThat(failure.getUserMessage()).contains("{cefr}", "{topicHint}", "{avoidTopics}",
                            "{lengthRules}");
                });
    }

    @Test
    void aTaskPromptMayNotCarryATokenNobodyFillsIn() {
        PromptContent task = shipped(AiPromptKind.GENERATOR, "READ_ALOUD");
        PromptContent withToken = new PromptContent(task.title(),
                List.of(Block.text("", "Write for level {cefr}.")), task.sections());

        assertThatThrownBy(() -> validator.validated(AiPromptKind.GENERATOR, withToken))
                .isInstanceOfSatisfying(AiPromptException.class,
                        failure -> assertThat(failure.getUserMessage()).contains("{cefr}"));
    }

    @Test
    void aPromptWithoutATitleOrContentIsRefused() {
        assertThatThrownBy(() -> validator.validated(AiPromptKind.GENERATOR,
                new PromptContent(" ", List.of(), List.of()))).isInstanceOfSatisfying(AiPromptException.class,
                failure -> assertThat(failure.getUserMessage())
                        .contains(PromptVersionConstants.PROBLEM_TITLE, PromptVersionConstants.PROBLEM_EMPTY));
    }

    @Test
    void duplicateSectionIdsAndEmptyBlocksAreRefused() {
        PromptContent.Section first = new PromptContent.Section("a", "A", List.of(Block.text("", "one")));
        PromptContent.Section duplicate = new PromptContent.Section("a", "B", List.of(Block.text("", "two")));
        PromptContent.Section empty = new PromptContent.Section("c", "C", List.of(Block.text("", "  ")));

        assertThatThrownBy(() -> validator.validated(AiPromptKind.GENERATOR,
                new PromptContent("T", List.of(), List.of(first, duplicate)))).isInstanceOf(AiPromptException.class);
        assertThatThrownBy(() -> validator.validated(AiPromptKind.GENERATOR,
                new PromptContent("T", List.of(), List.of(empty)))).isInstanceOf(AiPromptException.class);
    }
}
