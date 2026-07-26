package com.pte.authoring.service;

import com.pte.authoring.constant.AuthoringConstants;
import com.pte.authoring.domain.BlueprintItem;
import com.pte.authoring.domain.ExamBlueprint;
import com.pte.authoring.domain.Question;
import com.pte.authoring.domain.enums.PteSection;
import com.pte.authoring.domain.exception.BlueprintNotFoundException;
import com.pte.authoring.domain.exception.QuestionNotFoundException;
import com.pte.authoring.domain.exception.QuestionValidationException;
import com.pte.authoring.dto.request.BlueprintItemRequest;
import com.pte.authoring.dto.request.CreateBlueprintRequest;
import com.pte.authoring.dto.response.BlueprintResponse;
import com.pte.authoring.mapper.BlueprintMapper;
import com.pte.authoring.repository.ExamBlueprintRepository;
import com.pte.authoring.repository.QuestionRepository;
import com.pte.common.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Assembles blueprints from accessible questions. Each referenced question must
 * be readable by the caller (SHARED or own PRIVATE) — a host cannot compose with
 * another tenant's private content.
 */
@Service
public class BlueprintService {

    private final ExamBlueprintRepository blueprintRepository;
    private final QuestionRepository questionRepository;
    private final AuthoringAccessPolicy accessPolicy;

    public BlueprintService(ExamBlueprintRepository blueprintRepository, QuestionRepository questionRepository,
                            AuthoringAccessPolicy accessPolicy) {
        this.blueprintRepository = blueprintRepository;
        this.questionRepository = questionRepository;
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
        Question question = questionRepository.findWithOptionsByPublicId(request.questionPublicId())
                .orElseThrow(QuestionNotFoundException::new);
        if (!accessPolicy.canRead(question.getTenantId(), question.isShared(), caller)) {
            throw new QuestionNotFoundException();
        }
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
            throw new QuestionValidationException(AuthoringConstants.INVALID_QUESTION_FIELDS);
        }
    }
}
