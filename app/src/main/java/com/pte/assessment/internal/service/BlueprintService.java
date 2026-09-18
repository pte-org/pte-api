package com.pte.assessment.internal.service;

import com.pte.assessment.domain.BlueprintItem;
import com.pte.assessment.domain.ExamBlueprint;
import com.pte.assessment.domain.enums.BlueprintStatus;
import com.pte.assessment.dto.request.BlueprintItemRequest;
import com.pte.assessment.dto.request.CreateBlueprintRequest;
import com.pte.assessment.dto.request.RejectBlueprintRequest;
import com.pte.assessment.internal.dto.response.BlueprintResponse;
import com.pte.assessment.dto.response.SnapshotResponse;
import com.pte.assessment.internal.constant.AssessmentConstants;
import com.pte.assessment.internal.exception.BlueprintNotFoundException;
import com.pte.assessment.internal.exception.BlueprintValidationException;
import com.pte.assessment.internal.exception.BlueprintVersionConflictException;
import com.pte.assessment.internal.mapper.BlueprintMapper;
import com.pte.assessment.internal.repository.ExamBlueprintRepository;
import com.pte.itembank.ItembankService;
import com.pte.itembank.domain.enums.PteSection;
import com.pte.itembank.dto.response.QuestionFreezeView;
import com.pte.scoretemplate.ScoreTemplateService;
import com.pte.scoretemplate.dto.response.ScoreTemplateItemResponse;
import com.pte.scoretemplate.dto.response.ScoreTemplateResponse;
import com.pte.shared.security.CurrentUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Authoring lifecycle for manually curated exam blueprints. */
@Service
public class BlueprintService {

    private static final List<PteSection> SECTION_ORDER =
            List.of(PteSection.SPEAKING, PteSection.WRITING, PteSection.READING, PteSection.LISTENING);

    private final ExamBlueprintRepository blueprintRepository;
    private final AssessmentAccessPolicy accessPolicy;
    private final ItembankService itembankService;
    private final ScoreTemplateService scoreTemplateService;
    private final SnapshotPublishService snapshotPublishService;

    @Autowired
    public BlueprintService(ExamBlueprintRepository blueprintRepository, AssessmentAccessPolicy accessPolicy,
            ItembankService itembankService, ScoreTemplateService scoreTemplateService,
            SnapshotPublishService snapshotPublishService) {
        this.blueprintRepository = blueprintRepository;
        this.accessPolicy = accessPolicy;
        this.itembankService = itembankService;
        this.scoreTemplateService = scoreTemplateService;
        this.snapshotPublishService = snapshotPublishService;
    }

    /** Compatibility constructor for read-only unit tests and audit callers. */
    public BlueprintService(ExamBlueprintRepository blueprintRepository, AssessmentAccessPolicy accessPolicy) {
        this(blueprintRepository, accessPolicy, null, null, null);
    }

    @Transactional
    public BlueprintResponse create(CreateBlueprintRequest request, CurrentUser caller) {
        requireAuthor(caller);
        ExamBlueprint blueprint = new ExamBlueprint();
        blueprint.setName(request.name().trim());
        blueprint.setTenantId(caller.tenantId());
        replaceItems(blueprint, request.items(), Boolean.TRUE.equals(request.preserveOrder()));
        return BlueprintMapper.toResponse(blueprintRepository.save(blueprint));
    }

    @Transactional
    public BlueprintResponse update(UUID publicId, CreateBlueprintRequest request, CurrentUser caller) {
        ExamBlueprint blueprint = loadWritable(publicId, caller);
        requireStatus(blueprint, BlueprintStatus.DRAFT);
        checkVersion(blueprint, request.version());
        blueprint.setName(request.name().trim());
        replaceItems(blueprint, request.items(), Boolean.TRUE.equals(request.preserveOrder()));
        blueprint.setRejectionReason(null);
        return BlueprintMapper.toResponse(blueprint);
    }

    @Transactional
    public BlueprintResponse submitApproval(UUID publicId, CurrentUser caller) {
        ExamBlueprint blueprint = loadWritable(publicId, caller);
        requireStatus(blueprint, BlueprintStatus.DRAFT);
        validateItems(blueprint);
        blueprint.setRejectionReason(null);
        blueprint.setStatus(BlueprintStatus.PENDING_APPROVAL);
        return BlueprintMapper.toResponse(blueprint);
    }

    @Transactional
    public SnapshotResponse approve(UUID publicId, CurrentUser caller) {
        if (!accessPolicy.canApprove(caller)) {
            throw new AccessDeniedException("Only platform admins may approve blueprints");
        }
        ExamBlueprint blueprint = blueprintRepository.findWithItemsByPublicId(publicId)
                .orElseThrow(BlueprintNotFoundException::new);
        requireStatus(blueprint, BlueprintStatus.PENDING_APPROVAL);
        validateItems(blueprint);
        return snapshotPublishService.publish(publicId, caller);
    }

    @Transactional
    public BlueprintResponse reject(UUID publicId, RejectBlueprintRequest request, CurrentUser caller) {
        if (!accessPolicy.canApprove(caller)) {
            throw new AccessDeniedException("Only platform admins may reject blueprints");
        }
        ExamBlueprint blueprint = blueprintRepository.findWithItemsByPublicId(publicId)
                .orElseThrow(BlueprintNotFoundException::new);
        requireStatus(blueprint, BlueprintStatus.PENDING_APPROVAL);
        blueprint.setStatus(BlueprintStatus.DRAFT);
        blueprint.setRejectionReason(request.reason().trim());
        return BlueprintMapper.toResponse(blueprint);
    }

    @Transactional(readOnly = true)
    public BlueprintResponse get(UUID publicId, CurrentUser caller) {
        ExamBlueprint blueprint = blueprintRepository.findWithItemsByPublicId(publicId)
                .orElseThrow(BlueprintNotFoundException::new);
        if (!accessPolicy.canRead(blueprint.getTenantId(), blueprint.getTenantId() == null, caller)) {
            throw new BlueprintNotFoundException();
        }
        return BlueprintMapper.toResponse(blueprint);
    }

    @Transactional(readOnly = true)
    public List<BlueprintResponse> list(CurrentUser caller) {
        List<ExamBlueprint> blueprints = caller.isPlatformUser()
                ? blueprintRepository.findByTenantIdIsNull()
                : blueprintRepository.findByTenantId(caller.tenantId());
        return blueprints.stream().map(BlueprintMapper::toResponse).toList();
    }

    private ExamBlueprint loadWritable(UUID publicId, CurrentUser caller) {
        requireAuthor(caller);
        ExamBlueprint blueprint = blueprintRepository.findWithItemsByPublicId(publicId)
                .orElseThrow(BlueprintNotFoundException::new);
        if (!accessPolicy.canRead(blueprint.getTenantId(), blueprint.getTenantId() == null, caller)) {
            throw new BlueprintNotFoundException();
        }
        return blueprint;
    }

    private void requireAuthor(CurrentUser caller) {
        if (!accessPolicy.canAuthor(caller)) {
            throw new AccessDeniedException("Only platform authors may manage blueprints");
        }
    }

    private void requireStatus(ExamBlueprint blueprint, BlueprintStatus expected) {
        if (blueprint.getStatus() != expected) {
            throw new BlueprintValidationException(AssessmentConstants.BLUEPRINT_STATUS_INVALID);
        }
    }

    private void checkVersion(ExamBlueprint blueprint, Long expectedVersion) {
        if (expectedVersion != null && expectedVersion.longValue() != blueprint.getVersion()) {
            throw new BlueprintVersionConflictException();
        }
    }

    private void replaceItems(ExamBlueprint blueprint, List<BlueprintItemRequest> requestedItems,
            boolean preserveOrder) {
        ScoreTemplateResponse template = scoreTemplateService.getActive();
        Map<String, ScoreTemplateItemResponse> templateByTaskType = new HashMap<>();
        template.items().forEach(item -> templateByTaskType.put(item.taskType(), item));
        Set<UUID> seen = new HashSet<>();
        List<SortableItem> normalized = new ArrayList<>();

        for (int inputIndex = 0; inputIndex < requestedItems.size(); inputIndex++) {
            BlueprintItemRequest requested = requestedItems.get(inputIndex);
            if (!seen.add(requested.questionPublicId())) {
                throw new BlueprintValidationException(AssessmentConstants.BLUEPRINT_DUPLICATE_QUESTION);
            }
            QuestionFreezeView question = itembankService.freeze(requested.questionPublicId());
            ScoreTemplateItemResponse templateItem = templateByTaskType.get(question.pteTaskType().name());
            if (templateItem == null) {
                throw new BlueprintValidationException(AssessmentConstants.BLUEPRINT_ITEM_INVALID);
            }
            PteSection section = parseSection(templateItem.section());
            if (requested.section() != null && !requested.section().isBlank()
                    && !section.name().equals(requested.section())) {
                throw new BlueprintValidationException(AssessmentConstants.BLUEPRINT_ITEM_INVALID);
            }
            normalized.add(new SortableItem(requested.questionPublicId(), section, templateItem.sequence(),
                    requested.orderIndex() == null ? inputIndex : requested.orderIndex(), inputIndex));
        }

        if (preserveOrder) {
            normalized.sort(Comparator.comparingInt(SortableItem::requestedOrder)
                    .thenComparingInt(SortableItem::inputIndex));
        } else {
            normalized.sort(Comparator.comparingInt((SortableItem item) -> SECTION_ORDER.indexOf(item.section()))
                    .thenComparingInt(SortableItem::templateSequence)
                    .thenComparingInt(SortableItem::requestedOrder)
                    .thenComparingInt(SortableItem::inputIndex));
        }

        blueprint.getItems().clear();
        for (int orderIndex = 0; orderIndex < normalized.size(); orderIndex++) {
            SortableItem source = normalized.get(orderIndex);
            BlueprintItem item = new BlueprintItem();
            item.setQuestionPublicId(source.questionPublicId());
            item.setSection(source.section());
            item.setOrderIndex(orderIndex);
            blueprint.addItem(item);
        }
    }

    private void validateItems(ExamBlueprint blueprint) {
        if (blueprint.getItems().isEmpty()) {
            throw new BlueprintValidationException(AssessmentConstants.BLUEPRINT_ITEMS_REQUIRED);
        }
        Map<String, Integer> counts = new HashMap<>();
        blueprint.getItems().forEach(item -> {
            QuestionFreezeView question = itembankService.freeze(item.getQuestionPublicId());
            counts.merge(question.pteTaskType().name(), 1, Integer::sum);
        });
        ScoreTemplateResponse template = scoreTemplateService.getActive();
        for (ScoreTemplateItemResponse templateItem : template.items()) {
            int count = counts.getOrDefault(templateItem.taskType(), 0);
            if (count < templateItem.minCount() || count > templateItem.maxCount()) {
                throw new BlueprintValidationException(AssessmentConstants.BLUEPRINT_TEMPLATE_COMPLIANCE_INVALID);
            }
        }
    }

    private PteSection parseSection(String value) {
        try {
            return PteSection.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new BlueprintValidationException(AssessmentConstants.BLUEPRINT_ITEM_INVALID);
        }
    }

    private record SortableItem(UUID questionPublicId, PteSection section, int templateSequence,
            int requestedOrder, int inputIndex) {
    }
}
