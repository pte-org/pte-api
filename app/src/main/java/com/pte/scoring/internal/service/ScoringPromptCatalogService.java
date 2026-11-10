package com.pte.scoring.internal.service;

import com.pte.itembank.PromptDocument;
import com.pte.itembank.PromptDocument.Block;
import com.pte.itembank.PromptDocument.LevelRow;
import com.pte.itembank.PromptDocument.Section;
import com.pte.itembank.ScoringPromptSource;
import com.pte.scoring.internal.constant.ScoringPromptConstants;
import com.pte.scoring.internal.rubric.prompt.RubricPromptBuilder;
import com.pte.scoring.internal.vendor.gemini.scorer.GeminiPromptTexts;
import com.pte.scoring.rubric.RubricCatalog;
import com.pte.scoring.rubric.TaskRubric;
import com.pte.scoring.rubric.TraitDefinition;
import com.pte.scoring.rubric.TraitSource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Describes, for each task type, which engine scores each trait and what the AI scorer is sent: the examiner role, the
 * Score Guide level descriptions of the traits it scores and the guard. Traits that Azure or code scores are listed
 * as reference only because no model reads them. It reuses the builders the scorers use, so it cannot drift.
 */
@Service
public class ScoringPromptCatalogService implements ScoringPromptSource {

    private final RubricCatalog catalog;

    public ScoringPromptCatalogService(RubricCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public List<PromptDocument.Summary> summaries() {
        return catalog.all().stream()
                .sorted(Comparator.comparing(TaskRubric::taskType))
                .map(task -> new PromptDocument.Summary(ScoringPromptConstants.CATEGORY_SCORING, task.taskType(),
                        task.taskType(), engines(task)))
                .toList();
    }

    @Override
    public Optional<PromptDocument> find(String taskTypeKey) {
        return catalog.find(taskTypeKey).map(this::document);
    }

    private PromptDocument document(TaskRubric task) {
        List<Section> sections = new ArrayList<>();
        List<TraitDefinition> aiTraits = RubricPromptBuilder.llmTraits(task);
        Optional<String> examiner = GeminiPromptTexts.examiner(task.taskType());
        if (!aiTraits.isEmpty() && examiner.isPresent()) {
            sections.add(new Section(ScoringPromptConstants.SECTION_ROLE_ID,
                    ScoringPromptConstants.SECTION_ROLE_TITLE, false,
                    List.of(Block.text("", examiner.get()))));
            sections.add(levelsSection(aiTraits));
            notes(task).ifPresent(sections::add);
            sections.add(new Section(ScoringPromptConstants.SECTION_GUARD_ID,
                    ScoringPromptConstants.SECTION_GUARD_TITLE, true,
                    List.of(Block.text("", GeminiPromptTexts.guard()))));
        }
        referenceSection(task).ifPresent(sections::add);
        return new PromptDocument(ScoringPromptConstants.CATEGORY_SCORING, task.taskType(), task.taskType(),
                sections, GeminiPromptTexts.systemPrompt(task.taskType(), task).orElse(""));
    }

    private static List<String> engines(TaskRubric task) {
        List<String> engines = new ArrayList<>();
        if (task.isObjective()) {
            engines.add(ScoringPromptConstants.ENGINE_CODE);
        }
        for (TraitDefinition trait : task.traits()) {
            String engine = engineOf(trait.source());
            if (!engines.contains(engine)) {
                engines.add(engine);
            }
        }
        return engines;
    }

    private static String engineOf(TraitSource source) {
        return switch (source) {
            case AI_LLM -> ScoringPromptConstants.ENGINE_GEMINI;
            case AZURE -> ScoringPromptConstants.ENGINE_AZURE;
            case MECHANICAL, OBJECTIVE -> ScoringPromptConstants.ENGINE_CODE;
        };
    }

    private static Section levelsSection(List<TraitDefinition> aiTraits) {
        List<Block> blocks = new ArrayList<>();
        blocks.add(Block.text("", RubricPromptBuilder.INSTRUCTION));
        aiTraits.forEach(trait -> blocks.add(levelsBlock(trait, true)));
        return new Section(ScoringPromptConstants.SECTION_LEVELS_ID, ScoringPromptConstants.SECTION_LEVELS_TITLE,
                false, blocks);
    }

    private static Optional<Section> notes(TaskRubric task) {
        List<String> notes = new ArrayList<>();
        if (task.formRule() != null) {
            notes.add(RubricPromptBuilder.FORM_IS_CODED);
        }
        if (task.traits().stream().anyMatch(trait -> trait.source() == TraitSource.AZURE)) {
            notes.add(RubricPromptBuilder.SPEECH_IS_MEASURED);
        }
        return notes.isEmpty() ? Optional.empty() : Optional.of(new Section(ScoringPromptConstants.SECTION_NOTES_ID,
                ScoringPromptConstants.SECTION_NOTES_TITLE, false, List.of(Block.list("", notes))));
    }

    private static Optional<Section> referenceSection(TaskRubric task) {
        List<Block> blocks = task.traits().stream()
                .filter(trait -> trait.source() != TraitSource.AI_LLM)
                .map(trait -> levelsBlock(trait, false))
                .toList();
        return blocks.isEmpty() ? Optional.empty() : Optional.of(new Section(
                ScoringPromptConstants.SECTION_REFERENCE_ID, ScoringPromptConstants.SECTION_REFERENCE_TITLE, false,
                blocks));
    }

    private static Block levelsBlock(TraitDefinition trait, boolean sentToAi) {
        List<LevelRow> rows = trait.levels().stream()
                .map(level -> new LevelRow(level.level(), level.text()))
                .toList();
        return Block.levels(trait.displayNameEn(), rows, sentToAi);
    }
}
