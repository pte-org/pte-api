package com.pte.scoring.internal.rubric.service;

import com.pte.scoring.domain.ScoringRubricVersion;
import com.pte.scoring.domain.ScoringRubricVersion.RubricVersionStatus;
import com.pte.scoring.dto.response.RubricParametersView;
import com.pte.scoring.dto.response.TaskTypeParametersView;
import com.pte.scoring.internal.rubric.dto.AzureMappingResponse;
import com.pte.scoring.internal.rubric.repository.ScoringRubricVersionRepository;
import com.pte.scoring.rubric.EffectiveRubric;
import com.pte.scoring.rubric.FormRule;
import com.pte.scoring.rubric.TaskRubric;
import com.pte.scoring.rubric.TraitDefinition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The parameters of a pinned rubric version as a Host or Examiner may see them inside a score explanation: only an
 * ACTIVE or RETIRED version is readable (a draft, pending or rejected version yields nothing), and only the
 * trait-scored task types of the attempt are listed, in catalog order. The Azure mapping carries its calibration
 * flag and the "not calibrated with human-rater data" label.
 */
@Service
public class PinnedRubricParametersService {

    private static final Set<RubricVersionStatus> READABLE = Set.of(RubricVersionStatus.ACTIVE,
            RubricVersionStatus.RETIRED);

    private final ScoringRubricVersionRepository versionRepository;
    private final RubricResolver resolver;

    public PinnedRubricParametersService(ScoringRubricVersionRepository versionRepository, RubricResolver resolver) {
        this.versionRepository = versionRepository;
        this.resolver = resolver;
    }

    @Transactional(readOnly = true)
    public Optional<RubricParametersView> findReadable(UUID versionPublicId, Collection<String> taskTypes) {
        if (versionPublicId == null) {
            return Optional.empty();
        }
        return versionRepository.findByPublicId(versionPublicId)
                .filter(version -> READABLE.contains(version.getStatus()))
                .map(version -> toView(version, Set.copyOf(taskTypes)));
    }

    private RubricParametersView toView(ScoringRubricVersion version, Set<String> taskTypes) {
        List<TaskTypeParametersView> views = new ArrayList<>();
        resolver.effectiveRubrics(version).forEach((taskType, rubric) -> {
            if (taskTypes.contains(taskType) && !rubric.task().isObjective()) {
                views.add(toTaskView(rubric));
            }
        });
        return new RubricParametersView(version.getPublicId(), version.getVersionNo(), version.getStatus().name(),
                version.getCatalogVersion(), views);
    }

    private static TaskTypeParametersView toTaskView(EffectiveRubric rubric) {
        TaskRubric task = rubric.task();
        Map<String, Integer> weights = new LinkedHashMap<>();
        Map<String, Boolean> gates = new LinkedHashMap<>();
        for (TraitDefinition trait : task.traits()) {
            weights.put(trait.key(), rubric.weightOf(trait.key()));
            if (task.gateTraits().contains(trait.key())) {
                gates.put(trait.key(), rubric.isGateEnabled(trait.key()));
            }
        }
        FormRule formRule = rubric.effectiveFormRule();
        AzureMappingResponse azure = RubricResponseMapper.toAzureMapping(rubric);
        return new TaskTypeParametersView(task.taskType(), weights, formRule == null ? null : formRule.bands(),
                gates, azure == null ? null : azure.version(), azure == null ? null : azure.calibrated(),
                azure == null ? null : azure.label());
    }
}
