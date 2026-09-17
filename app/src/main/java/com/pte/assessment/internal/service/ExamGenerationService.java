package com.pte.assessment.internal.service;

import com.pte.assessment.domain.BlueprintItem;
import com.pte.assessment.domain.ExamBlueprint;
import com.pte.assessment.dto.response.SnapshotResponse;
import com.pte.assessment.internal.exception.InsufficientQuestionBankException;
import com.pte.assessment.internal.exception.InsufficientQuestionBankException.Shortage;
import com.pte.assessment.internal.exception.InvalidSectionException;
import com.pte.assessment.internal.exception.InvalidSkillSelectionException;
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
import java.util.Comparator;
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
                requirements.add(new Requirement(PteTaskType.PERSONAL_INTRODUCTION, section, 1, 1));
            }
            itemsBySection.getOrDefault(section, List.of()).stream()
                    .sorted(Comparator.comparingInt(ScoreTemplateItemResponse::sequence))
                    .forEach(i -> requirements.add(new Requirement(
                            PteTaskType.valueOf(i.taskType()), section, i.minCount(), i.maxCount())));
        }
        return requirements;
    }

    private RolledRequirement roll(Requirement requirement) {
        int n = requirement.minCount() == requirement.maxCount()
                ? requirement.minCount()
                : requirement.minCount() + random.nextInt(requirement.maxCount() - requirement.minCount() + 1);
        return new RolledRequirement(requirement.taskType(), requirement.section(), n);
    }

    private void checkStockOrThrow(List<RolledRequirement> rolled) {
        Set<PteTaskType> taskTypes = rolled.stream().map(RolledRequirement::taskType).collect(Collectors.toSet());
        Map<PteTaskType, Long> counts = itembankService.countPublishedByTaskTypes(taskTypes);
        List<Shortage> shortages = new ArrayList<>();
        for (RolledRequirement r : rolled) {
            long available = counts.getOrDefault(r.taskType(), 0L);
            if (available < r.n()) {
                shortages.add(new Shortage(r.taskType().name(), r.n(), (int) available));
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
            List<UUID> ids = itembankService.randomPublishedQuestionIds(r.taskType(), r.n());
            if (ids.size() != r.n()) {
                shortages.add(new Shortage(r.taskType().name(), r.n(), ids.size()));
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

    private record Requirement(PteTaskType taskType, PteSection section, int minCount, int maxCount) {
    }

    private record RolledRequirement(PteTaskType taskType, PteSection section, int n) {
    }

    private record PickedItem(UUID questionPublicId, PteSection section) {
    }
}
