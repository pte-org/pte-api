package com.pte.scoring.internal.service;

import com.pte.itembank.GenerationRubricTraits;
import com.pte.scoring.rubric.RubricCatalog;
import com.pte.scoring.rubric.TaskRubric;
import com.pte.scoring.rubric.TraitDefinition;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Adapts RubricCatalog to the GenerationRubricTraits SPI for use in question generation.
 * Decouples generation context from the scoring module's internal rubric representation.
 */
@Service
public class GenerationRubricTraitsService implements GenerationRubricTraits {

    private final RubricCatalog rubricCatalog;

    public GenerationRubricTraitsService(RubricCatalog rubricCatalog) {
        this.rubricCatalog = rubricCatalog;
    }

    @Override
    public Optional<TaskTraitInfo> find(String taskTypeKey) {
        return rubricCatalog.find(taskTypeKey).map(this::adaptTaskRubric);
    }

    private TaskTraitInfo adaptTaskRubric(TaskRubric taskRubric) {
        List<TraitInfo> traits = new ArrayList<>();
        for (TraitDefinition traitDef : taskRubric.traits()) {
            traits.add(adaptTraitDefinition(traitDef));
        }
        return new TaskTraitInfo(taskRubric.taskType(), traits);
    }

    private TraitInfo adaptTraitDefinition(TraitDefinition traitDef) {
        Integer maxScore = traitDef.scale().fixedMax();
        if (maxScore == null) {
            maxScore = 5; // default for per-word traits
        }

        List<LevelDescriptor> levels = new ArrayList<>();
        for (int i = 0; i < traitDef.levels().size(); i++) {
            String text = traitDef.levels().get(i).text();
            if (text != null) {
                levels.add(new LevelDescriptor(i, text));
            }
        }

        return new TraitInfo(traitDef.key(), traitDef.displayNameEn(), maxScore, levels);
    }
}
