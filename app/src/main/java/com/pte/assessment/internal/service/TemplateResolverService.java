package com.pte.assessment.internal.service;

import com.pte.assessment.domain.BlueprintItem;
import com.pte.assessment.domain.ExamBlueprint;
import com.pte.assessment.dto.response.SnapshotResponse;
import com.pte.assessment.dto.response.TemplateSpec;
import com.pte.assessment.internal.exception.InsufficientQuestionsException;
import com.pte.assessment.internal.exception.InsufficientQuestionsException.MissingQuestionSlot;
import com.pte.assessment.internal.repository.ExamBlueprintRepository;
import com.pte.itembank.ItembankService;
import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.itembank.dto.response.QuestionFreezeView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Resolves an active platform template into a deterministic immutable snapshot. */
@Service
public class TemplateResolverService {

    private final TemplateService templateService;
    private final ItembankService itembankService;
    private final ExamBlueprintRepository blueprintRepository;
    private final SnapshotPublishService snapshotPublishService;

    public TemplateResolverService(TemplateService templateService, ItembankService itembankService,
                                   ExamBlueprintRepository blueprintRepository,
                                   SnapshotPublishService snapshotPublishService) {
        this.templateService = templateService;
        this.itembankService = itembankService;
        this.blueprintRepository = blueprintRepository;
        this.snapshotPublishService = snapshotPublishService;
    }

    @Transactional
    public SnapshotResponse resolve(UUID templatePublicId, long seed) {
        TemplateSpec spec = templateService.getTemplateSpec(templatePublicId);
        List<SlotPlan> slots = orderedSlots(spec);
        Map<PteTaskType, Long> availableByType = availableByType(slots);
        List<MissingQuestionSlot> missing = missingSlots(slots, availableByType);
        if (!missing.isEmpty()) {
            throw new InsufficientQuestionsException(missing);
        }

        Map<PteTaskType, Integer> requiredByType = requiredByType(slots);
        Map<PteTaskType, List<QuestionFreezeView>> selectedByType = new HashMap<>();
        for (Map.Entry<PteTaskType, Integer> requirement : requiredByType.entrySet()) {
            selectedByType.put(requirement.getKey(), itembankService.findRandomByTaskType(
                    requirement.getKey(), requirement.getValue(), seed));
        }
        Map<PteTaskType, Long> selectedCounts = selectedByType.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,
                        entry -> (long) entry.getValue().size()));
        List<MissingQuestionSlot> selectionMisses = missingSlots(slots, selectedCounts);
        if (!selectionMisses.isEmpty()) {
            throw new InsufficientQuestionsException(selectionMisses);
        }

        List<SnapshotPublishService.GeneratedItem> generatedItems = new ArrayList<>();
        Map<PteTaskType, Integer> offsetsByType = new HashMap<>();
        for (SlotPlan slot : slots) {
            List<QuestionFreezeView> questions = selectedByType.get(slot.taskType());
            int offset = offsetsByType.getOrDefault(slot.taskType(), 0);
            for (QuestionFreezeView question : questions.subList(offset, offset + slot.questionCount())) {
                generatedItems.add(new SnapshotPublishService.GeneratedItem(
                        slot.section().section(), generatedItems.size(), question));
            }
            offsetsByType.put(slot.taskType(), offset + slot.questionCount());
        }

        ExamBlueprint blueprint = new ExamBlueprint();
        blueprint.setName(spec.name());
        blueprint.setTenantId(null);
        for (SnapshotPublishService.GeneratedItem generated : generatedItems) {
            BlueprintItem item = new BlueprintItem();
            item.setQuestionPublicId(generated.question().sourceQuestionPublicId());
            item.setSection(generated.section());
            item.setOrderIndex(generated.orderIndex());
            blueprint.addItem(item);
        }
        ExamBlueprint savedBlueprint = blueprintRepository.save(blueprint);
        return snapshotPublishService.publishGenerated(templatePublicId, seed, savedBlueprint, spec, generatedItems);
    }

    private List<SlotPlan> orderedSlots(TemplateSpec spec) {
        return spec.sections().stream()
                .sorted(Comparator.comparingInt(TemplateSpec.Section::orderIndex))
                .flatMap(section -> section.slots().stream()
                        .sorted(Comparator.comparingInt(TemplateSpec.Slot::orderIndex))
                        .map(slot -> new SlotPlan(section, slot)))
                .toList();
    }

    private Map<PteTaskType, Long> availableByType(List<SlotPlan> slots) {
        Map<PteTaskType, Long> available = new HashMap<>();
        slots.stream().map(SlotPlan::taskType).distinct()
                .forEach(taskType -> available.put(taskType, itembankService.countAvailableByTaskType(taskType)));
        return available;
    }

    private Map<PteTaskType, Integer> requiredByType(List<SlotPlan> slots) {
        Map<PteTaskType, Integer> required = new HashMap<>();
        slots.forEach(slot -> required.merge(slot.taskType(), slot.questionCount(), Math::addExact));
        return required;
    }

    private List<MissingQuestionSlot> missingSlots(List<SlotPlan> slots, Map<PteTaskType, Long> availableByType) {
        Map<PteTaskType, Long> remaining = new HashMap<>(availableByType);
        List<MissingQuestionSlot> missing = new ArrayList<>();
        for (SlotPlan slot : slots) {
            long available = remaining.getOrDefault(slot.taskType(), 0L);
            if (available < slot.questionCount()) {
                missing.add(new MissingQuestionSlot(slot.taskType().name(), slot.questionCount(), available));
            }
            remaining.put(slot.taskType(), Math.max(0, available - slot.questionCount()));
        }
        return List.copyOf(missing);
    }

    private record SlotPlan(TemplateSpec.Section section, TemplateSpec.Slot slot) {

        private PteTaskType taskType() {
            return slot.taskType();
        }

        private int questionCount() {
            return slot.questionCount();
        }
    }
}
