package com.pte.assessment.internal.service;

import com.pte.assessment.domain.ExamBlueprint;
import com.pte.assessment.internal.dto.response.BlueprintResponse;
import com.pte.assessment.internal.exception.BlueprintNotFoundException;
import com.pte.assessment.internal.mapper.BlueprintMapper;
import com.pte.assessment.internal.repository.ExamBlueprintRepository;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Reads resolver-generated blueprints for platform audit. */
@Service
public class BlueprintService {

    private final ExamBlueprintRepository blueprintRepository;
    private final AssessmentAccessPolicy accessPolicy;

    public BlueprintService(ExamBlueprintRepository blueprintRepository, AssessmentAccessPolicy accessPolicy) {
        this.blueprintRepository = blueprintRepository;
        this.accessPolicy = accessPolicy;
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
}
