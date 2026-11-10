package com.pte.assessment.internal.service.standard;

import com.pte.assessment.domain.ExamDefinition;
import com.pte.assessment.dto.response.ExamDefinitionItemResponse;
import com.pte.assessment.dto.response.ExamDefinitionResponse;
import com.pte.assessment.internal.mapper.ExamDefinitionMapper;
import com.pte.assessment.internal.repository.ExamDefinitionRepository;
import com.pte.itembank.TaskTypeStimulusService;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Maps host templates to responses carrying the "Pearson standard" label against the template they came from. */
public final class HostTemplateLabeler {

    private final ExamDefinitionRepository repository;
    private final TaskTypeStimulusService stimulusService;

    public HostTemplateLabeler(ExamDefinitionRepository repository, TaskTypeStimulusService stimulusService) {
        this.repository = repository;
        this.stimulusService = stimulusService;
    }

    /** Responses in the order of {@code templates}; templates without a known origin carry no label. */
    public List<ExamDefinitionResponse> label(List<ExamDefinition> templates) {
        List<ExamDefinitionResponse> responses = templates.stream().map(ExamDefinitionMapper::toResponse).toList();
        Map<UUID, ExamDefinitionResponse> bases = loadBases(responses);
        Map<String, Integer> stimulusSeconds = stimulusSeconds(responses, bases);
        return responses.stream().map(response -> {
            ExamDefinitionResponse base = bases.get(response.baseDefinitionPublicId());
            if (base == null) {
                return response;
            }
            StandardCheckResult result = HostTemplateStandardCheck.evaluate(response, base, stimulusSeconds);
            return response.withStandardLabel(result.standard(), result.deviations());
        }).toList();
    }

    private Map<UUID, ExamDefinitionResponse> loadBases(List<ExamDefinitionResponse> responses) {
        Map<UUID, ExamDefinitionResponse> bases = new HashMap<>();
        responses.stream().map(ExamDefinitionResponse::baseDefinitionPublicId)
                .filter(java.util.Objects::nonNull).distinct()
                .forEach(baseId -> repository.findWithItemsByPublicId(baseId)
                        .ifPresent(base -> bases.put(baseId, ExamDefinitionMapper.toResponse(base))));
        return bases;
    }

    private Map<String, Integer> stimulusSeconds(List<ExamDefinitionResponse> responses,
            Map<UUID, ExamDefinitionResponse> bases) {
        if (stimulusService == null) {
            return Map.of();
        }
        Set<String> keys = new HashSet<>();
        responses.forEach(response -> response.items().forEach(item -> keys.add(item.taskTypeKey())));
        bases.values().forEach(base -> base.items().stream().map(ExamDefinitionItemResponse::taskTypeKey)
                .forEach(keys::add));
        return stimulusService.stimulusBeforeTimer(keys).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().seconds()));
    }
}
