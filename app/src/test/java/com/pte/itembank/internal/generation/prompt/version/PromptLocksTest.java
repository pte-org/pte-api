package com.pte.itembank.internal.generation.prompt.version;

import com.pte.itembank.PromptDocument;
import com.pte.itembank.PromptDocument.Block;
import com.pte.itembank.internal.generation.domain.AiPromptKind;
import com.pte.itembank.internal.generation.prompt.PromptContent;
import com.pte.itembank.internal.generation.prompt.PromptTestRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;

class PromptLocksTest {

    private static final String KEY = "ANSWER_SHORT_QUESTION";

    private static PromptContent shipped(AiPromptKind kind, String key) {
        return PromptTestRegistry.codec().read(PromptTestRegistry.find(kind, key).orElseThrow().getContent());
    }

    private static PromptContent withSection(PromptContent content, String id,
                                             UnaryOperator<PromptContent.Section> change) {
        List<PromptContent.Section> sections = new ArrayList<>();
        content.sections().forEach(section -> sections.add(section.id().equals(id) ? change.apply(section) : section));
        return new PromptContent(content.title(), content.intro(), sections);
    }

    private static PromptContent.Section withFields(PromptContent.Section section, List<String> rows) {
        return new PromptContent.Section(section.id(), section.title(), section.blocks().stream()
                .map(block -> block.type() == PromptDocument.BlockType.FIELDS ? Block.fields("", rows) : block)
                .toList());
    }

    private static List<String> rowsOf(PromptContent content) {
        return content.sections().stream().flatMap(section -> section.blocks().stream())
                .filter(block -> block.type() == PromptDocument.BlockType.FIELDS).findFirst().orElseThrow().items();
    }

    @Test
    void editingANormalSectionOrAnOutputValueIsAllowed() {
        PromptContent live = shipped(AiPromptKind.GENERATOR, KEY);
        PromptContent requirements = withSection(live, "requirements",
                section -> new PromptContent.Section(section.id(), section.title(), List.of(Block.text("", "New."))));
        List<String> rows = new ArrayList<>(rowsOf(live));
        rows.set(0, rows.get(0).substring(0, rows.get(0).indexOf(':') + 1) + " Health");
        PromptContent output = withSection(live, "output", section -> withFields(section, rows));

        assertThat(PromptLocks.problems(AiPromptKind.GENERATOR, KEY, live, requirements)).isEmpty();
        assertThat(PromptLocks.problems(AiPromptKind.GENERATOR, KEY, live, output)).isEmpty();
    }

    @Test
    void theInstructionsSectionCannotBeChangedOrRemoved() {
        PromptContent live = shipped(AiPromptKind.GENERATOR, KEY);
        PromptContent changed = withSection(live, "instructions",
                section -> new PromptContent.Section(section.id(), section.title(), List.of(Block.text("", "Any."))));
        PromptContent removed = new PromptContent(live.title(), live.intro(), live.sections().stream()
                .filter(section -> !section.id().equals("instructions")).toList());

        assertThat(PromptLocks.problems(AiPromptKind.GENERATOR, KEY, live, changed)).hasSize(1);
        assertThat(PromptLocks.problems(AiPromptKind.GENERATOR, KEY, live, removed)).hasSize(1);
    }

    @Test
    void anOutputFieldCannotBeRenamedOrDropped() {
        PromptContent live = shipped(AiPromptKind.GENERATOR, KEY);
        List<String> rows = rowsOf(live);
        PromptContent dropped = withSection(live, "output", section -> withFields(section, rows.subList(1, rows.size())));
        List<String> renamed = new ArrayList<>(rows);
        renamed.set(0, "renamed: x");
        PromptContent renamedField = withSection(live, "output", section -> withFields(section, renamed));

        assertThat(PromptLocks.problems(AiPromptKind.GENERATOR, KEY, live, dropped)).hasSize(1);
        assertThat(PromptLocks.problems(AiPromptKind.GENERATOR, KEY, live, renamedField)).hasSize(1);
    }

    @Test
    void theSharedContextIsLocked() {
        PromptContent live = shipped(AiPromptKind.SHARED, "COMMON_GENERATOR");
        PromptContent changed = withSection(live, "context",
                section -> new PromptContent.Section(section.id(), section.title(), List.of(Block.text("", "{cefr}"))));

        assertThat(PromptLocks.lockedSectionIds(AiPromptKind.SHARED, "COMMON_GENERATOR"))
                .contains("context", "json-format", "answer-hints");
        assertThat(PromptLocks.problems(AiPromptKind.SHARED, "COMMON_GENERATOR", live, changed)).hasSize(1);
    }
}
