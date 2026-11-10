package com.pte.scoring.internal.rubric.catalog;

import com.pte.scoring.internal.constant.RubricConstants;
import com.pte.scoring.internal.rubric.catalog.speaking.AnswerShortQuestionRubric;
import com.pte.scoring.internal.rubric.catalog.speaking.DescribeImageRubric;
import com.pte.scoring.internal.rubric.catalog.speaking.ReadAloudRubric;
import com.pte.scoring.internal.rubric.catalog.speaking.RepeatSentenceRubric;
import com.pte.scoring.internal.rubric.catalog.speaking.RespondToSituationRubric;
import com.pte.scoring.internal.rubric.catalog.speaking.RetellLectureRubric;
import com.pte.scoring.internal.rubric.catalog.speaking.SummarizeGroupDiscussionRubric;
import com.pte.scoring.internal.rubric.catalog.writing.SummarizeSpokenTextRubric;
import com.pte.scoring.internal.rubric.catalog.writing.SummarizeWrittenTextRubric;
import com.pte.scoring.internal.rubric.catalog.writing.WriteEssayRubric;
import com.pte.scoring.rubric.RubricCatalog;
import com.pte.scoring.rubric.RubricParameters;
import com.pte.scoring.rubric.RubricScoreException;
import com.pte.scoring.rubric.TaskRubric;
import com.pte.scoring.rubric.TraitDefinition;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The 22 standard task-type rubrics, declared in code from the Pearson PTE Academic Score Guide 2025. */
@Component
public class RubricCatalogImpl implements RubricCatalog {

    private final List<TaskRubric> rubrics;
    private final Map<String, TaskRubric> byTaskType = new HashMap<>();

    public RubricCatalogImpl() {
        List<TaskRubric> all = new ArrayList<>(List.of(
                ReadAloudRubric.create(), RepeatSentenceRubric.create(), DescribeImageRubric.create(),
                RetellLectureRubric.create(), SummarizeGroupDiscussionRubric.create(),
                RespondToSituationRubric.create(), AnswerShortQuestionRubric.create(),
                SummarizeWrittenTextRubric.create(), WriteEssayRubric.create(), SummarizeSpokenTextRubric.create()));
        all.addAll(ObjectiveRubrics.all());
        this.rubrics = List.copyOf(all);
        rubrics.forEach(rubric -> byTaskType.put(rubric.taskType(), rubric));
    }

    @Override
    public Optional<TaskRubric> find(String taskType) {
        return Optional.ofNullable(byTaskType.get(taskType));
    }

    @Override
    public List<TaskRubric> all() {
        return rubrics;
    }

    @Override
    public int catalogVersion() {
        return RubricConstants.CATALOG_VERSION;
    }

    @Override
    public RubricParameters defaultParameters(String taskType) {
        TaskRubric rubric = find(taskType)
                .orElseThrow(() -> new RubricScoreException(RubricConstants.RUBRIC_TASK_TYPE_UNKNOWN));
        Map<String, Integer> weights = new LinkedHashMap<>();
        for (TraitDefinition trait : rubric.traits()) {
            weights.put(trait.key(), trait.scale().isPerWord()
                    ? RubricConstants.DEFAULT_PER_WORD_WEIGHT : trait.scale().maxFor(null));
        }
        Map<String, Boolean> gates = new LinkedHashMap<>();
        rubric.gateTraits().forEach(gate -> gates.put(gate, true));
        return new RubricParameters(weights, rubric.formRule() == null ? null : rubric.formRule().bands(), gates,
                null);
    }
}
