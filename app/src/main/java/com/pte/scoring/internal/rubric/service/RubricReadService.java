package com.pte.scoring.internal.rubric.service;

import com.pte.scoring.domain.ScoringRubricVersion;
import com.pte.scoring.domain.ScoringRubricVersion.RubricVersionStatus;
import com.pte.scoring.internal.constant.RubricConstants;
import com.pte.scoring.internal.exception.RubricVersionException;
import com.pte.scoring.internal.rubric.dto.RubricVersionResponse;
import com.pte.scoring.internal.rubric.dto.TaskTypeRubricResponse;
import com.pte.scoring.internal.rubric.repository.ScoringRubricVersionRepository;
import com.pte.scoring.rubric.RubricCatalog;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Read side of the vendor rubric pages: the rubric of every task type in a version (ACTIVE by default). */
@Service
public class RubricReadService {

    private final ScoringRubricVersionRepository versionRepository;
    private final RubricCatalog catalog;
    private final RubricResolver resolver;

    private final RubricUserNames names;

    @Autowired
    public RubricReadService(ScoringRubricVersionRepository versionRepository, RubricCatalog catalog,
                             RubricResolver resolver, RubricUserNames names) {
        this.versionRepository = versionRepository;
        this.catalog = catalog;
        this.resolver = resolver;
        this.names = names;
    }

    /** Without user names, for callers that only need the numbers. */
    public RubricReadService(ScoringRubricVersionRepository versionRepository, RubricCatalog catalog,
                             RubricResolver resolver) {
        this(versionRepository, catalog, resolver, null);
    }

    @Transactional(readOnly = true)
    public List<TaskTypeRubricResponse> listTaskTypes(UUID versionPublicId) {
        ScoringRubricVersion version = versionOrActive(versionPublicId);
        return resolver.effectiveRubrics(version).values().stream()
                .map(rubric -> RubricResponseMapper.toTaskTypeResponse(rubric, version,
                        catalog.defaultParameters(rubric.task().taskType()).traitWeights())).toList();
    }

    /** An unknown task type is a 404 before any version is read. */
    @Transactional(readOnly = true)
    public TaskTypeRubricResponse getTaskType(String taskType, UUID versionPublicId) {
        if (catalog.find(taskType).isEmpty()) {
            throw new RubricVersionException(HttpStatus.NOT_FOUND, RubricConstants.RUBRIC_TASK_TYPE_UNKNOWN);
        }
        ScoringRubricVersion version = versionOrActive(versionPublicId);
        return RubricResponseMapper.toTaskTypeResponse(resolver.effectiveFor(version, taskType), version,
                catalog.defaultParameters(taskType).traitWeights());
    }

    @Transactional(readOnly = true)
    public List<RubricVersionResponse> listVersions() {
        return versionRepository.findAllByOrderByVersionNoDesc().stream()
                .map(version -> RubricResponseMapper.toVersionResponse(version, names)).toList();
    }

    @Transactional(readOnly = true)
    public RubricVersionResponse getVersion(UUID versionPublicId) {
        return RubricResponseMapper.toVersionResponse(load(versionPublicId), names);
    }

    private ScoringRubricVersion versionOrActive(UUID versionPublicId) {
        if (versionPublicId != null) {
            return load(versionPublicId);
        }
        return versionRepository.findByStatus(RubricVersionStatus.ACTIVE)
                .orElseThrow(() -> new RubricVersionException(HttpStatus.NOT_FOUND,
                        RubricConstants.RUBRIC_VERSION_NO_ACTIVE));
    }

    private ScoringRubricVersion load(UUID versionPublicId) {
        return versionRepository.findByPublicId(versionPublicId)
                .orElseThrow(() -> new RubricVersionException(HttpStatus.NOT_FOUND,
                        RubricConstants.RUBRIC_VERSION_NOT_FOUND));
    }
}
