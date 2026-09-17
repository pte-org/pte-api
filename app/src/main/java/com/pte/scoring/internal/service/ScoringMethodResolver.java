package com.pte.scoring.internal.service;

import com.pte.scoretemplate.ScoreTemplateService;
import com.pte.scoretemplate.dto.response.ScoreTemplateItemResponse;
import com.pte.scoring.domain.enums.ScoringMethod;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Replaces the old hardcoded AI/objective task-type catalogs: {@code
 * scoringMethod} per task type comes from the {@code ScoreTemplate} pinned
 * to the answer's attempt (spec FR-07), resolved through {@code
 * ScoreTemplateService} rather than two parallel hardcoded sets.
 *
 * <p>Cached per {@code scoreTemplatePublicId} for the JVM's lifetime — safe
 * because a {@code ScoreTemplate}, once pinned to any snapshot, is
 * immutable (ACTIVE/RETIRED never change, per {@code
 * ScoreTemplateAdminService}'s write-once convention). Avoids re-fetching
 * the same template once per answer in a session's scoring loop.
 */
@Service
public class ScoringMethodResolver {

    private final ScoreTemplateService scoreTemplateService;
    private final Map<UUID, Map<String, ScoringMethod>> cacheByTemplate = new ConcurrentHashMap<>();

    public ScoringMethodResolver(ScoreTemplateService scoreTemplateService) {
        this.scoreTemplateService = scoreTemplateService;
    }

    /** Empty when {@code taskType} has no row in this template (e.g. PERSONAL_INTRODUCTION) — callers treat that the same as UNSCORED: leave the answer PENDING. */
    public Optional<ScoringMethod> resolve(UUID scoreTemplatePublicId, String taskType) {
        Map<String, ScoringMethod> byTaskType = cacheByTemplate.computeIfAbsent(scoreTemplatePublicId, this::loadTemplate);
        return Optional.ofNullable(byTaskType.get(taskType));
    }

    private Map<String, ScoringMethod> loadTemplate(UUID scoreTemplatePublicId) {
        return scoreTemplateService.getByPublicId(scoreTemplatePublicId).items().stream()
                .collect(Collectors.toMap(ScoreTemplateItemResponse::taskType,
                        item -> ScoringMethod.valueOf(item.scoringMethod())));
    }
}
