package com.pte.assessment.internal.service;

import com.pte.assessment.domain.BlueprintItem;
import com.pte.assessment.domain.ExamBlueprint;
import com.pte.assessment.internal.constant.AssessmentConstants;
import com.pte.assessment.dto.response.ExamDefinitionFeasibilityResponse;
import com.pte.assessment.dto.response.CefrTargetResponse;
import com.pte.assessment.dto.response.ExamDefinitionItemResponse;
import com.pte.assessment.dto.response.ExamDefinitionResponse;
import com.pte.assessment.dto.response.ExamDefinitionSlotFeasibilityResponse;
import com.pte.assessment.internal.exception.InsufficientQuestionBankException;
import com.pte.assessment.internal.service.assembly.AssemblyReport;
import com.pte.assessment.internal.service.assembly.CountSelection;
import com.pte.assessment.internal.service.assembly.CefrMix;
import com.pte.assessment.internal.service.assembly.CountSelector;
import com.pte.assessment.internal.service.assembly.FreshQuestions;
import com.pte.assessment.internal.service.assembly.StratifiedQuestionPicker.Need;
import com.pte.assessment.internal.service.assembly.StratifiedQuestionPicker.Picked;
import com.pte.itembank.ExamCandidateService;
import com.pte.itembank.domain.enums.CefrLevel;
import com.pte.itembank.ItembankService;
import com.pte.itembank.TaskTypeStimulusService;
import com.pte.itembank.TaskTypeCodeCompatibility;
import com.pte.itembank.domain.enums.PteSection;
import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.shared.security.CurrentUser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Assembles exam blueprints by drawing items from the question bank according to
 * requirements derived from an active template. Handles both random and deterministic
 * generation modes, with TOCTOU-safe stock checking.
 */
class ExamBlueprintAssembler {

    private final ItembankService itembankService;
    private final CountSelector countSelector;
    private final StratifiedDraw stratifiedDraw;

    ExamBlueprintAssembler(ItembankService itembankService) {
        this(itembankService, null);
    }

    ExamBlueprintAssembler(ItembankService itembankService, TaskTypeStimulusService stimulusService) {
        this(itembankService, stimulusService, null);
    }

    ExamBlueprintAssembler(ItembankService itembankService, TaskTypeStimulusService stimulusService,
            ExamCandidateService candidateService) {
        this.stratifiedDraw = new StratifiedDraw(candidateService);
        this.itembankService = itembankService;
        this.countSelector = new CountSelector(stimulusService);
    }

    /** Validates skill input before template lookup. Throws exception if empty or invalid. */
    void validateSkills(Set<String> skills) {
        SkillSelection.validate(skills);
    }

    /**
     * Builds exam blueprint by collecting requirements from template,
     * rolling random counts, checking stock, and drawing items.
     */
    ExamBlueprint assembleRandomBlueprint(String name, Set<String> skills,
            ExamDefinitionResponse template, CurrentUser caller) {
        Set<PteSection> selectedSections = SkillSelection.parse(skills);
        List<Requirement> requirements = buildRequirements(template, selectedSections, true);
        List<RolledRequirement> rolled = requirements.stream().map(this::roll).toList();

        checkStockOrThrow(rolled);
        return createBlueprint(name, drawOrThrow(rolled), caller);
    }

    /** Builds an exam blueprint with deterministic generation using a fixed seed. */
    ExamBlueprint assembleDeterministicBlueprint(String name, UUID templatePublicId, long seed,
            CurrentUser caller, ExamDefinitionResponse template, Set<String> selectedSkills) {
        return assembleWithReport(name, seed, caller, template, selectedSkills).blueprint();
    }

    /** Deterministic generation that also reports how the counts were decided. */
    AssembledBlueprint assembleWithReport(String name, long seed, CurrentUser caller,
            ExamDefinitionResponse template, Set<String> selectedSkills) {
        return assembleWithReport(name, seed, caller, template, selectedSkills, null, FreshQuestions.none());
    }

    /** A non-empty {@code cefrOverride} replaces the template's CEFR mix; an active {@code fresh} prefers unused ones. */
    AssembledBlueprint assembleWithReport(String name, long seed, CurrentUser caller,
            ExamDefinitionResponse template, Set<String> selectedSkills, List<CefrTargetResponse> cefrOverride,
            FreshQuestions fresh) {
        Set<PteSection> selectedSections = SkillSelection.resolve(template, selectedSkills);
        List<Requirement> requirements = buildRequirements(template, selectedSections,
                !"CUSTOM".equalsIgnoreCase(template.templatePolicy()));
        CountSelection selection = countSelector.select(template, selectedSections, seed);
        List<RolledRequirement> rolled = requirements.stream()
                .map(requirement -> selection.isPlanned()
                        ? new RolledRequirement(requirement.taskTypeKey(), requirement.standardTaskType(),
                                requirement.section(),
                                selection.counts().getOrDefault(requirement.taskTypeKey(), requirement.minCount()))
                        : roll(requirement, new java.util.Random(seed ^ requirement.taskTypeKey().hashCode())))
                .toList();
        checkStockOrThrow(rolled);
        Map<CefrLevel, Integer> mix = CefrMix.of(cefrOverride == null || cefrOverride.isEmpty() ? template.cefrTargets() : cefrOverride);
        List<Need> needs = rolled.stream().map(r -> new Need(r.taskTypeKey(), r.section().name(), r.n())).toList();
        Picked stratified = mix.isEmpty() || !stratifiedDraw.isAvailable() ? null
                : stratifiedDraw.draw(needs, mix, seed, fresh);
        List<PickedItem> picked = stratified == null ? drawDeterministic(rolled, seed, fresh)
                : stratifiedDraw.toItems(needs, stratified);
        Map<String, Integer> finalCounts = rolled.stream()
                .collect(Collectors.toMap(RolledRequirement::taskTypeKey, RolledRequirement::n, Integer::sum));
        AssemblyReport report = selection.report(seed, finalCounts)
                .withRepeated(fresh.repeatedIn(picked.stream().map(PickedItem::questionPublicId).toList()));
        return new AssembledBlueprint(createBlueprint(name, picked, caller),
                stratified == null ? report : report.withCefr(stratified.report()));
    }

    /** Scope-aware host readiness using the exact same requirement resolver as deterministic generation. */
    ExamDefinitionFeasibilityResponse templateFeasibility(ExamDefinitionResponse template, Set<String> selectedSkills) {
        Set<PteSection> selectedSections = SkillSelection.resolve(template, selectedSkills);
        List<Requirement> requirements = buildRequirements(template, selectedSections,
                !"CUSTOM".equalsIgnoreCase(template.templatePolicy()));
        List<RolledRequirement> required = requirements.stream()
                .map(item -> new RolledRequirement(item.taskTypeKey(), item.standardTaskType(), item.section(),
                        item.maxCount()))
                .toList();
        Map<String, Long> available = publishedCounts(required);
        List<ExamDefinitionSlotFeasibilityResponse> slots = requirements.stream()
                .map(item -> {
                    long count = available.getOrDefault(item.taskTypeKey(), 0L);
                    boolean ready = count >= item.maxCount();
                    return new ExamDefinitionSlotFeasibilityResponse(item.taskTypeKey(), item.section().name(),
                            item.maxCount(), count, ready, ready ? null : "INSUFFICIENT_POOL");
                })
                .toList();
        return new ExamDefinitionFeasibilityResponse(template.publicId(), template.version(),
                !slots.isEmpty() && slots.stream().allMatch(ExamDefinitionSlotFeasibilityResponse::ready), slots);
    }

    private List<Requirement> buildRequirements(ExamDefinitionResponse template, Set<PteSection> selectedSections,
            boolean includeImplicitPersonalIntroduction) {
        Map<PteSection, List<ExamDefinitionItemResponse>> itemsBySection = template.items().stream()
                .collect(Collectors.groupingBy(i -> PteSection.valueOf(i.section())));

        List<Requirement> requirements = new ArrayList<>();
        for (PteSection section : AssessmentConstants.EXAM_SECTION_ORDER) {
            if (!selectedSections.contains(section)) {
                continue;
            }
            if (includeImplicitPersonalIntroduction && section == PteSection.SPEAKING) {
                requirements.add(new Requirement(PteTaskType.PERSONAL_INTRODUCTION.name(),
                        PteTaskType.PERSONAL_INTRODUCTION, section, 1, 1));
            }
            itemsBySection.getOrDefault(section, List.of()).stream()
                    .sorted(Comparator.comparingInt(i -> TemplateTaskOrder.sequenceInSection(section,
                            TaskTypeCodeCompatibility.normalizeTaskTypeKey(
                                    i.taskTypeKey() == null ? i.taskType() : i.taskTypeKey()),
                            i.sequence())))
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
        return roll(requirement, new java.util.Random());
    }

    private RolledRequirement roll(Requirement requirement, java.util.Random source) {
        int n = requirement.minCount() == requirement.maxCount()
                ? requirement.minCount()
                : requirement.minCount() + source.nextInt(requirement.maxCount() - requirement.minCount() + 1);
        return new RolledRequirement(requirement.taskTypeKey(), requirement.standardTaskType(),
                requirement.section(), n);
    }

    private List<PickedItem> drawDeterministic(List<RolledRequirement> rolled, long seed, FreshQuestions fresh) {
        List<PickedItem> picked = new ArrayList<>();
        Set<UUID> used = new HashSet<>();
        List<InsufficientQuestionBankException.Shortage> shortages = new ArrayList<>();
        for (RolledRequirement requirement : rolled) {
            List<UUID> candidates = new ArrayList<>(
                    fresh.prefer(publishedQuestionIds(requirement), id -> id, requirement.n()));
            Collections.shuffle(candidates, new java.util.Random(seed ^ requirement.taskTypeKey().hashCode()
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
                shortages.add(new InsufficientQuestionBankException.Shortage(requirement.taskTypeKey(), requirement.n(), selected));
            }
        }
        if (!shortages.isEmpty()) {
            throw new InsufficientQuestionBankException(shortages);
        }
        return picked;
    }

    private void checkStockOrThrow(List<RolledRequirement> rolled) {
        Map<String, Long> available = publishedCounts(rolled);
        List<InsufficientQuestionBankException.Shortage> shortages = new ArrayList<>();
        for (RolledRequirement r : rolled) {
            long count = available.getOrDefault(r.taskTypeKey(), 0L);
            if (count < r.n()) {
                shortages.add(new InsufficientQuestionBankException.Shortage(r.taskTypeKey(), r.n(), (int) count));
            }
        }
        if (!shortages.isEmpty()) {
            throw new InsufficientQuestionBankException(shortages);
        }
    }

    private List<PickedItem> drawOrThrow(List<RolledRequirement> rolled) {
        List<PickedItem> picked = new ArrayList<>();
        List<InsufficientQuestionBankException.Shortage> shortages = new ArrayList<>();
        for (RolledRequirement r : rolled) {
            List<UUID> ids = r.standardTaskType() == null
                    ? itembankService.randomPublishedQuestionIdsByTaskTypeKey(r.taskTypeKey(), r.n())
                    : itembankService.randomPublishedQuestionIds(r.standardTaskType(), r.n());
            if (ids.size() != r.n()) {
                shortages.add(new InsufficientQuestionBankException.Shortage(r.taskTypeKey(), r.n(), ids.size()));
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

    private Map<String, Long> publishedCounts(List<RolledRequirement> requirements) {
        boolean allStandard = requirements.stream().allMatch(requirement -> requirement.standardTaskType() != null);
        if (allStandard) {
            return itembankService.countPublishedByTaskTypes(requirements.stream()
                            .map(RolledRequirement::standardTaskType).collect(Collectors.toSet()))
                    .entrySet().stream()
                    .collect(Collectors.toMap(entry -> entry.getKey().name(), Map.Entry::getValue));
        }
        return itembankService.countPublishedByTaskTypeKeys(requirements.stream()
                .map(RolledRequirement::taskTypeKey).collect(Collectors.toSet()));
    }

    private ExamBlueprint createBlueprint(String name, List<PickedItem> picked, CurrentUser caller) {
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
        return blueprint;
    }

    private record Requirement(String taskTypeKey, PteTaskType standardTaskType, PteSection section,
            int minCount, int maxCount) {
    }

    private record RolledRequirement(String taskTypeKey, PteTaskType standardTaskType, PteSection section, int n) {
    }

    record PickedItem(UUID questionPublicId, PteSection section) {
    }

    record AssembledBlueprint(ExamBlueprint blueprint, AssemblyReport report) {
    }
}
