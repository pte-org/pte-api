package com.pte.assessment.internal.service;

import com.pte.assessment.domain.BlueprintItem;
import com.pte.assessment.domain.ExamBlueprint;
import com.pte.assessment.internal.dto.request.BlueprintItemRequest;
import com.pte.assessment.internal.dto.request.CreateBlueprintRequest;
import com.pte.assessment.internal.dto.response.BlueprintResponse;
import com.pte.assessment.internal.exception.BlueprintNotFoundException;
import com.pte.assessment.internal.exception.InvalidSectionException;
import com.pte.assessment.internal.mapper.BlueprintMapper;
import com.pte.assessment.internal.repository.ExamBlueprintRepository;
import com.pte.itembank.ItembankService;
import com.pte.itembank.domain.enums.PteSection;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Assembles blueprints from accessible questions. Each referenced question must
 * be readable by the caller (SHARED or own PRIVATE) — a host cannot compose with
 * another tenant's private content. Existence/accessibility is checked through
 * {@link ItembankService#get}, never {@code itembank}'s repository directly
 * (Phase 05 Design Constraints: {@code assessment → itembank → shared} only).
 */
@Service
public class BlueprintService {

    private final ExamBlueprintRepository blueprintRepository;
    private final ItembankService itembankService;
    private final AssessmentAccessPolicy accessPolicy;

    public BlueprintService(ExamBlueprintRepository blueprintRepository, ItembankService itembankService,
                            AssessmentAccessPolicy accessPolicy) {
        this.blueprintRepository = blueprintRepository;
        this.itembankService = itembankService;
        this.accessPolicy = accessPolicy;
    }

    @Transactional
    public BlueprintResponse create(CreateBlueprintRequest request, CurrentUser caller) {
        ExamBlueprint blueprint = new ExamBlueprint();
        blueprint.setName(request.name());
        blueprint.setTenantId(caller.tenantId());
        request.items().forEach(item -> blueprint.addItem(buildItem(item, caller)));
        return BlueprintMapper.toResponse(blueprintRepository.save(blueprint));
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
        UUID tenantId = caller.tenantId();
        if (tenantId == null) {
            return List.of();
        }
        return blueprintRepository.findByTenantId(tenantId).stream().map(BlueprintMapper::toResponse).toList();
    }

    private BlueprintItem buildItem(BlueprintItemRequest request, CurrentUser caller) {
        // Throws itembank's own QuestionNotFoundException (404) when the question
        // doesn't exist or isn't readable by caller — same externally-observable
        // behavior as the pre-split direct-repository check.
        itembankService.get(request.questionPublicId(), caller);
        BlueprintItem item = new BlueprintItem();
        item.setQuestionPublicId(request.questionPublicId());
        item.setSection(parseSection(request.section()));
        item.setOrderIndex(request.orderIndex());
        return item;
    }

    private PteSection parseSection(String value) {
        try {
            return PteSection.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new InvalidSectionException();
        }
    }
}
