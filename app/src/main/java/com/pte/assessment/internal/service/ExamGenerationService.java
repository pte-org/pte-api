package com.pte.assessment.internal.service;

import com.pte.itembank.TaskTypeCodeCompatibility;
import com.pte.assessment.domain.BlueprintItem;
import com.pte.assessment.domain.ExamBlueprint;
import com.pte.assessment.dto.response.SnapshotResponse;
import com.pte.assessment.internal.exception.InsufficientQuestionBankException;
import com.pte.assessment.internal.exception.InsufficientQuestionBankException.Shortage;
import com.pte.assessment.internal.exception.InvalidSectionException;
import com.pte.assessment.internal.exception.InvalidSkillSelectionException;
import com.pte.assessment.internal.constant.AssessmentConstants;
import com.pte.assessment.internal.exception.TemplateNotActiveException;
import com.pte.assessment.internal.repository.ExamBlueprintRepository;
import com.pte.itembank.ItembankService;
import com.pte.itembank.domain.enums.PteSection;
import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.scoretemplate.ScoreTemplateService;
import com.pte.scoretemplate.dto.response.ScoreTemplateItemResponse;
import com.pte.scoretemplate.dto.response.ScoreTemplateResponse;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Draws a random exam blueprint from the PUBLISHED+SHARED question bank,
 * shaped by the ACTIVE {@link ScoreTemplateService#getActive() ScoreTemplate}
 * and the caller's chosen skills, then publishes it into an {@link
 * com.pte.assessment.domain.ExamSnapshot} — all in one transaction (Plan B,
 * plans/score-template-exam-generation, Phase 2).
 *
 * <p>Never passes {@link CurrentUser} to {@link ItembankService}'s
 * generation facade — the pool is always PUBLISHED+SHARED, independent of
 * which host is generating (2026-09-17 platform-only decision).
 */
@Service
public class ExamGenerationService {

    /** Fixed section draw order — the natural PTE test-day order, and also the V5 template's own sequence order. */
    private static final List<PteSection> SECTION_ORDER =
            List.of(PteSection.SPEAKING, PteSection.WRITING, PteSection.READING, PteSection.LISTENING);

    private final ScoreTemplateService scoreTemplateService;
    private final ItembankService itembankService;
    private final ExamBlueprintRepository blueprintRepository;
    private final SnapshotPublishService snapshotPublishService;
    private final Random random = new Random();

    public ExamGenerationService(ScoreTemplateService scoreTemplateService, ItembankService itembankService,
                                 ExamBlueprintRepository blueprintRepository,
                                 SnapshotPublishService snapshotPublishService) {
        this.scoreTemplateService = scoreTemplateService;
        this.itembankService = itembankService;
        this.blueprintRepository = blueprintRepository;
        this.snapshotPublishService = snapshotPublishService;
    }

    @Transactional
    public SnapshotResponse generate(String name, Set<String> skills, CurrentUser caller) {
        Set<PteSection> selectedSections = parseSkills(skills);
        ScoreTemplateResponse template = scoreTemplateService.getActive();
        List<Requirement> requirements = buildRequirements(template, selectedSections);
        List<RolledRequirement> rolled = requirements.stream().map(this::roll).toList();

        checkStockOrThrow(rolled);
        List<PickedItem> picked = drawOrThrow(rolled);

        ExamBlueprint blueprint = new ExamBlueprint();
        blueprint.setName(name);
        blueprint.setTenantId(caller.tenantId());
        int orderIndex = 0;
        for (PickedItem item : picked) {
            BlueprintItem blueprintItem = new BlueprintItem();
            blueprintItem.setQuestionPublicId(item.questionPublicId());
            blueprintItem.setSection(item.section());
            blueprintItem.setOrderIndex(orderIndex++);
            blueprint.addItem(blueprintItem);
        }

        ExamBlueprint saved = blueprintRepository.save(blueprint);
        return snapshotPublishService.publish(saved.getPublicId(), caller);
    }

    /** Deterministic, template-pinned generation used by the new session flow. */
    @Transactional
    public SnapshotResponse generateDeterministic(String name, UUID templatePublicId, long seed,
            CurrentUser caller) {
        ScoreTemplateResponse template = scoreTemplateService.findActiveByPublicId(templatePublicId)
                .orElseThrow(TemplateNotActiveException::new);
        List<Requirement> requirements = buildRequirements(template, Set.of(
                PteSection.SPEAKING, PteSection.WRITING, PteSection.READING, PteSection.LISTENING));
        List<RolledRequirement> rolled = requirements.stream()
                .map(requirement -> roll(requirement, new Random(seed ^ requirement.taskTypeKey().hashCode())))
                .toList();
        checkStockOrThrow(rolled);
        List<PickedItem> picked = drawDeterministic(rolled, seed);

        ExamBlueprint blueprint = new ExamBlueprint();
        blueprint.setName(name);
        blueprint.setTenantId(caller.tenantId());
        int orderIndex = 0;
        for (PickedItem item : picked) {
            BlueprintItem blueprintItem = new BlueprintItem();
            blueprintItem.setQuestionPublicId(item.questionPublicId());
            blueprintItem.setSection(item.section());
            blueprintItem.setOrderIndex(orderIndex++);
            blueprint.addItem(blueprintItem);
        }
        ExamBlueprint saved = blueprintRepository.save(blueprint);
        return snapshotPublishService.publish(saved.getPublicId(), caller, template, seed,
                AssessmentConstants.DETERMINISTIC_ALGORITHM_VERSION,
                template.publicId() + ":" + template.version());
    }

    private Set<PteSection> parseSkills(Set<String> skills) {
        if (skills == null || skills.isEmpty() || skills.size() > 4) {
            throw new InvalidSkillSelectionException();
        }
        return skills.stream().map(this::parseSection).collect(Collectors.toSet());
    }

    private PteSection parseSection(String value) {
        try {
            return PteSection.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new InvalidSectionException();
        }
    }

    private List<Requirement> buildRequirements(ScoreTemplateResponse template, Set<PteSection> selectedSections) {
        Map<PteSection, List<ScoreTemplateItemResponse>> itemsBySection = template.items().stream()
                .collect(Collectors.groupingBy(i -> PteSection.valueOf(i.section())));

        List<Requirement> requirements = new ArrayList<>();
        for (PteSection section : SECTION_ORDER) {
            if (!selectedSections.contains(section)) {
                continue;
            }
            if (section == PteSection.SPEAKING) {
                    requirements.add(new Requirement(PteTaskType.PERSONAL_INTRODUCTION.name(),
                            PteTaskType.PERSONAL_INTRODUCTION, section, 1, 1));
            }
            itemsBySection.getOrDefault(section, List.of()).stream()
                    .sorted(Comparator.comparingInt(ScoreTemplateItemResponse::sequence))
                    .forEach(i -> {
                        String taskTypeKey = TaskTypeCodeCompatibility.normalizeTaskTypeKey(
                                i.taskTypeKey() == null ? i.taskType() : i.taskTypeKey());
                        PteTaskType standard = TaskTypeCodeCompatibility.isStandard(taskTypeKey)
                                ? TaskTypeCodeCompatibility.parse(taskTypeKey) : null;
                        requirements.add(new Requirement(taskTypeKey, standard, section, i.minCount(), i.maxCount()));
                    });
        }
        return requirements;
    }

    private RolledRequirement roll(Requirement requirement) {
        return roll(requirement, random);
    }

    private RolledRequirement roll(Requirement requirement, Random source) {
        int n = requirement.minCount() == requirement.maxCount()
                ? requirement.minCount()
                : requirement.minCount() + source.nextInt(requirement.maxCount() - requirement.minCount() + 1);
        return new RolledRequirement(requirement.taskTypeKey(), requirement.standardTaskType(),
                requirement.section(), n);
    }

    private List<PickedItem> drawDeterministic(List<RolledRequirement> rolled, long seed) {
        List<PickedItem> picked = new ArrayList<>();
        Set<UUID> used = new HashSet<>();
        List<Shortage> shortages = new ArrayList<>();
        for (RolledRequirement requirement : rolled) {
            List<UUID> candidates = new ArrayList<>(publishedQuestionIds(requirement));
            Collections.shuffle(candidates, new Random(seed ^ requirement.taskTypeKey().hashCode()
                    ^ requirement.section().name().hashCode()));
            int selected = 0;
            for (UUID candidate : candidates) {
                if (used.add(candidate)) {
                    picked.add(new PickedItem(candidate, requirement.section()));
                    selected++;
                    if (selected == requirement.n()) {
                        break;
                    }
                }
            }
            if (selected < requirement.n()) {
                shortages.add(new Shortage(requirement.taskTypeKey(), requirement.n(), selected));
            }
        }
        if (!shortages.isEmpty()) {
            throw new InsufficientQuestionBankException(shortages);
        }
        return picked;
    }

    private void checkStockOrThrow(List<RolledRequirement> rolled) {
        boolean allStandard = rolled.stream().allMatch(requirement -> requirement.standardTaskType() != null);
        Map<PteTaskType, Long> standardCounts = allStandard
                ? itembankService.countPublishedByTaskTypes(rolled.stream()
                        .map(RolledRequirement::standardTaskType).collect(Collectors.toSet()))
                : Map.of();
        Map<String, Long> dynamicCounts = allStandard ? Map.of()
                : itembankService.countPublishedByTaskTypeKeys(rolled.stream()
                        .map(RolledRequirement::taskTypeKey).collect(Collectors.toSet()));
        List<Shortage> shortages = new ArrayList<>();
        for (RolledRequirement r : rolled) {
            long available = r.standardTaskType() == null
                    ? dynamicCounts.getOrDefault(r.taskTypeKey(), 0L)
                    : standardCounts.getOrDefault(r.standardTaskType(), 0L);
            if (available < r.n()) {
                shortages.add(new Shortage(r.taskTypeKey(), r.n(), (int) available));
            }
        }
        if (!shortages.isEmpty()) {
            throw new InsufficientQuestionBankException(shortages);
        }
    }

    /**
     * TOCTOU guard (red-team): stock was enough at {@link #checkStockOrThrow}, but a
     * concurrent archive under READ COMMITTED can still shrink it before this draw.
     * Every requirement is drawn — even after one comes up short — so a second
     * concurrent shortage isn't hidden behind the first; nothing is saved either way.
     */
    private List<PickedItem> drawOrThrow(List<RolledRequirement> rolled) {
        List<PickedItem> picked = new ArrayList<>();
        List<Shortage> shortages = new ArrayList<>();
        for (RolledRequirement r : rolled) {
            List<UUID> ids = r.standardTaskType() == null
                    ? itembankService.randomPublishedQuestionIdsByTaskTypeKey(r.taskTypeKey(), r.n())
                    : itembankService.randomPublishedQuestionIds(r.standardTaskType(), r.n());
            if (ids.size() != r.n()) {
                shortages.add(new Shortage(r.taskTypeKey(), r.n(), ids.size()));
                continue;
            }
            for (UUID id : ids) {
                picked.add(new PickedItem(id, r.section()));
            }
        }
        if (!shortages.isEmpty()) {
            throw new InsufficientQuestionBankException(shortages);
        }
        return picked;
    }

    private List<UUID> publishedQuestionIds(RolledRequirement requirement) {
        return requirement.standardTaskType() == null
                ? itembankService.publishedQuestionIdsByTaskTypeKey(requirement.taskTypeKey())
                : itembankService.publishedQuestionIds(requirement.standardTaskType());
    }

    private record Requirement(String taskTypeKey, PteTaskType standardTaskType, PteSection section,
            int minCount, int maxCount) {
    }

    private record RolledRequirement(String taskTypeKey, PteTaskType standardTaskType, PteSection section, int n) {
    }

    private record PickedItem(UUID questionPublicId, PteSection section) {
    }
}
