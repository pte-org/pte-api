package com.pte.scoring;

import com.pte.scoring.internal.rubric.service.RubricResolver;
import com.pte.scoring.rubric.EffectiveRubric;
import com.pte.scoring.rubric.RubricCatalog;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** Read-only view of the ACTIVE rubric of one task type, so other modules can show its criteria to students. */
@Service
public class RubricCriteriaService {

    private final RubricCatalog catalog;
    private final RubricResolver resolver;

    public RubricCriteriaService(RubricCatalog catalog, RubricResolver resolver) {
        this.catalog = catalog;
        this.resolver = resolver;
    }

    /** Empty for a task type the rubric catalog does not cover (objective tasks have no levels to show). */
    public Optional<EffectiveRubric> activeRubric(String taskType) {
        if (taskType == null || catalog.find(taskType).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(resolver.resolveActiveVersion(taskType).rubric());
    }
}
