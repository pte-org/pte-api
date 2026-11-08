package com.pte.itembank.internal.generation.prompt;

import com.pte.itembank.PromptDocument;
import com.pte.itembank.PromptDocument.Block;
import com.pte.itembank.internal.generation.domain.AiPromptKind;
import com.pte.itembank.internal.generation.exception.AiPromptException;
import com.pte.itembank.internal.generation.prompt.version.PromptContentValidator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptFieldsJsonTest {

    @Test
    void plainTextBecomesAStringAndJsonStaysJson() {
        String json = PromptFieldsJson.compose(List.of(
                "topic: Environmental Science",
                "options: [\"Rising\", \"Falling\"]",
                "correctIndexes: [0, 2]",
                "keyPoints: null",
                "count: 5",
                "range: 50-60 words",
                "answer: wind power / wind energy"));

        assertThat(json).isEqualTo("""
                {
                  "topic": "Environmental Science",
                  "options": [
                    "Rising",
                    "Falling"
                  ],
                  "correctIndexes": [
                    0,
                    2
                  ],
                  "keyPoints": null,
                  "count": 5,
                  "range": "50-60 words",
                  "answer": "wind power / wind energy"
                }""");
    }

    @Test
    void textThatStartsLikeJsonButIsNotStaysText() {
        String json = PromptFieldsJson.compose(List.of("a: true story", "b: 12 monkeys", "c: \"123\""));

        assertThat(json).contains("\"a\": \"true story\"").contains("\"b\": \"12 monkeys\"").contains("\"c\": \"123\"");
    }

    @Test
    void nestedValuesAreIndentedAndTheResultIsValidJson() throws Exception {
        String json = PromptFieldsJson.compose(List.of(
                "turns: [{\"speaker\": \"A\", \"text\": \"Hello there.\"}]",
                "empty: []",
                "quote: She said \"hi\"")).strip();

        assertThat(json).contains("    {\n      \"speaker\": \"A\"");
        assertThat(JsonMapper.builder().build().readTree(json).get("quote").asText()).isEqualTo("She said \"hi\"");
    }

    @Test
    void aRowWithoutANameIsLeftOutAndNoRowsGiveAnEmptyObject() {
        assertThat(PromptFieldsJson.compose(List.of(": nothing", "no separator", "ok: 1"))).contains("\"ok\": 1")
                .doesNotContain("nothing");
        assertThat(PromptFieldsJson.compose(List.of())).isEqualTo("{}");
    }

    @Test
    void theOutputRowsAreSentAsAJsonExampleInsideAFence() {
        PromptContent content = new PromptContent("T", List.of(), List.of(new PromptContent.Section("output", "Output",
                List.of(Block.fields("", List.of("topic: Daily Life"))))));

        assertThat(PromptTextAssembler.assemble(content)).contains("## Output\n\n```json\n{\n  \"topic\": \"Daily Life\"\n}\n```");
    }

    @Test
    void theValidatorKeepsRowsAndRefusesRowsWithoutAUniqueName() {
        PromptContentValidator validator = new PromptContentValidator();
        PromptContent good = new PromptContent("T", List.of(), List.of(new PromptContent.Section("o", "Output",
                List.of(Block.fields("", List.of(" topic :  A ", "n: 1"))))));

        assertThat(validator.validated(AiPromptKind.GENERATOR, good).sections().get(0).blocks().get(0).items())
                .containsExactly("topic: A", "n: 1");
        for (List<String> rows : List.of(List.of("no name here"), List.of("a: 1", "a: 2"), List.<String>of())) {
            PromptContent bad = new PromptContent("T", List.of(Block.text("", "x")), List.of(
                    new PromptContent.Section("o", "Output", List.of(Block.fields("", rows)))));
            assertThatThrownBy(() -> validator.validated(AiPromptKind.GENERATOR, bad))
                    .isInstanceOf(AiPromptException.class);
        }
    }

    @Test
    void everyShippedPromptHasNoMarkdownBoldAndEveryGeneratorOutputIsRows() {
        PromptContentCodec codec = PromptTestRegistry.codec();
        PromptTestRegistry.seededVersions().forEach(version -> {
            PromptContent content = codec.read(version.getContent());
            assertThat(PromptTextAssembler.assemble(content)).as(version.getPromptKey()).doesNotContain("**");
            if (version.getKind() == AiPromptKind.GENERATOR) {
                assertThat(content.sections().stream().flatMap(section -> section.blocks().stream())
                        .filter(block -> block.type() == PromptDocument.BlockType.FIELDS)
                        .flatMap(block -> block.items().stream())).as(version.getPromptKey())
                        .anyMatch(row -> row.startsWith("topic:"));
                assertThat(content.sections().stream().flatMap(section -> section.blocks().stream()))
                        .as(version.getPromptKey()).noneMatch(block -> block.type() == PromptDocument.BlockType.CODE);
            }
        });
    }
}
