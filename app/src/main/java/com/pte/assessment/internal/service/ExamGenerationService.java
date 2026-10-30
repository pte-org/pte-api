package com.pte.assessment.internal.service;

import com.pte.assessment.domain.ExamBlueprint;
import com.pte.assessment.dto.response.ExamDefinitionFeasibilityResponse;
import com.pte.assessment.dto.response.SnapshotResponse;
import com.pte.assessment.internal.constant.AssessmentConstants;
import com.pte.assessment.internal.exception.TemplateNotActiveException;
import com.pte.assessment.internal.repository.ExamBlueprintRepository;
import com.pte.itembank.ItembankService;
import com.pte.assessment.ExamDefinitionService;
import com.pte.assessment.dto.response.ExamDefinitionResponse;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/**
 * Draws exam blueprints from the PUBLISHED+SHARED question bank, shaped by the ACTIVE
 * exam template and the caller's chosen skills, then publishes into an immutable
 * snapshot — all in one transaction (Plan B, plans/score-template-exam-generation, Phase 2).
 *
 * <p>Never passes {@link CurrentUser} to {@link ItembankService}'s generation facade —
 * the pool is always PUBLISHED+SHARED, independent of which host is generating.
 */
@Service
public class ExamGenerationService {

    private final ExamDefinitionService examDefinitionService;
    private final ItembankService itembankService;
    private final ExamBlueprintRepository blueprintRepository;
    private final SnapshotPublishService snapshotPublishService;
    private final ExamBlueprintAssembler assembler;

    public ExamGenerationService(ExamDefinitionService examDefinitionService, ItembankService itembankService,
                                 ExamBlueprintRepository blueprintRepository,
                                 SnapshotPublishService snapshotPublishService) {
        this.examDefinitionService = examDefinitionService;
        this.itembankService = itembankService;
        this.blueprintRepository = blueprintRepository;
        this.snapshotPublishService = snapshotPublishService;
        this.assembler = new ExamBlueprintAssembler(itembankService);
    }

    @Transactional
    public SnapshotResponse generate(String name, Set<String> skills, CurrentUser caller) {
        assembler.validateSkills(skills);  // Validate early before hitting DB
        ExamDefinitionResponse template = examDefinitionService.getActive();
        ExamBlueprint blueprint = assembler.assembleRandomBlueprint(name, skills, template, caller);
        ExamBlueprint saved = blueprintRepository.save(blueprint);
        return snapshotPublishService.publish(saved.getPublicId(), caller);
    }

    /** Deterministic, template-pinned generation used by the new session flow. */
    @Transactional
    public SnapshotResponse generateDeterministic(String name, UUID templatePublicId, long seed,
            CurrentUser caller) {
        return generateDeterministic(name, templatePublicId, seed, caller, null);
    }

    /** Deterministic generation constrained to the session's persisted template sections. */
    @Transactional
    public SnapshotResponse generateDeterministic(String name, UUID templatePublicId, long seed,
            CurrentUser caller, Set<String> selectedSkills) {
        ExamDefinitionResponse template = examDefinitionService.findActiveByPublicId(templatePublicId)
                .orElseThrow(TemplateNotActiveException::new);
        ExamBlueprint blueprint = assembler.assembleDeterministicBlueprint(name, templatePublicId, seed, caller, template, selectedSkills);
        ExamBlueprint saved = blueprintRepository.save(blueprint);
        return snapshotPublishService.publish(saved.getPublicId(), caller, template, seed,
                AssessmentConstants.DETERMINISTIC_ALGORITHM_VERSION,
                template.publicId() + ":" + template.version());
    }

    /** Scope-aware feasibility check for template generation. */
    @Transactional(readOnly = true)
    public ExamDefinitionFeasibilityResponse getTemplateFeasibility(UUID templatePublicId,
            Set<String> selectedSkills) {
        return assembler.templateFeasibility(examDefinitionService.getByPublicId(templatePublicId), selectedSkills);
    }
}
