package com.pte.assessment.internal.service;

import com.pte.assessment.ExamDefinitionService;
import com.pte.assessment.dto.response.CefrTargetResponse;
import com.pte.assessment.dto.response.ExamDefinitionItemResponse;
import com.pte.assessment.dto.response.ExamDefinitionResponse;
import com.pte.assessment.dto.response.GenerationCefrMixResponse;
import com.pte.assessment.dto.response.GenerationPresetResponse;
import com.pte.itembank.domain.enums.CefrLevel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The AI-generation presets: one row per task type of the active platform exam template with its {@code maxCount}
 * (a task type listed twice keeps the larger count; rows with no item are left out). Ordered as in the template (its
 * sequence already follows the exam's section order, so sorting by section name would scramble it). No active template answers an empty list, so the author UI simply hides the presets.
 */
@Service
public class GenerationPresetQueryService {

    private final ExamDefinitionService examDefinitions;

    public GenerationPresetQueryService(ExamDefinitionService examDefinitions) {
        this.examDefinitions = examDefinitions;
    }

    @Transactional(readOnly = true)
    public List<GenerationPresetResponse> listActivePresets() {
        return examDefinitions.findActive().map(GenerationPresetQueryService::presetsOf).orElse(List.of());
    }

    /** The active template's CEFR mix, lowest level first; empty when there is none or it sets no share. */
    @Transactional(readOnly = true)
    public Optional<GenerationCefrMixResponse> findActiveCefrMix() {
        return examDefinitions.findActive().flatMap(GenerationPresetQueryService::cefrMixOf);
    }

    private static Optional<GenerationCefrMixResponse> cefrMixOf(ExamDefinitionResponse template) {
        List<CefrTargetResponse> targets = template.cefrTargets() == null ? List.of()
                : template.cefrTargets().stream()
                        .filter(target -> target.percent() > 0)
                        .sorted(Comparator.comparing(target -> CefrLevel.valueOf(target.level())))
                        .toList();
        return targets.isEmpty() ? Optional.empty()
                : Optional.of(new GenerationCefrMixResponse(template.name(), template.version(), targets));
    }

    private static List<GenerationPresetResponse> presetsOf(ExamDefinitionResponse template) {
        if (template.items() == null) {
            return List.of();
        }
        List<ExamDefinitionItemResponse> ordered = template.items().stream()
                .filter(item -> item.taskType() != null && item.maxCount() >= 1)
                .sorted(Comparator.comparingInt(ExamDefinitionItemResponse::sequence))
                .toList();
        Map<String, GenerationPresetResponse> byTaskType = new LinkedHashMap<>();
        for (ExamDefinitionItemResponse item : ordered) {
            byTaskType.merge(item.taskType(),
                    new GenerationPresetResponse(item.taskType(), item.section(), item.maxCount()),
                    (kept, next) -> kept.maxCount() >= next.maxCount() ? kept
                            : new GenerationPresetResponse(kept.taskType(), kept.section(), next.maxCount()));
        }
        return List.copyOf(byTaskType.values());
    }
}
