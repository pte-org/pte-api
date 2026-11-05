package com.pte.scoring;

import com.pte.scoring.internal.rubric.service.RubricResolver;
import com.pte.scoring.rubric.EffectiveRubric;
import com.pte.scoring.rubric.TraitDefinition;
import com.pte.shared.exception.DomainException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The maximum of each trait of a stored rubric version, so a reader can show Pearson trait scores as "achieved /
 * max". Form's maximum comes from the version's Form bands; a per-word trait (Read Aloud Content) has no fixed
 * maximum and is left out. Scores are only ever stored against ACTIVE or RETIRED versions, which are immutable, so a
 * version's maxima are cached once read. Display-only: a version that cannot be read yields no maxima rather than an
 * error.
 */
@Service
public class RubricScaleService {

    private record Key(UUID versionPublicId, String taskType) {
    }

    private final RubricResolver resolver;
    private final Map<Key, Map<String, Integer>> maxima = new ConcurrentHashMap<>();

    public RubricScaleService(RubricResolver resolver) {
        this.resolver = resolver;
    }

    public Optional<Map<String, Integer>> traitMaxima(UUID rubricVersionPublicId, String taskType) {
        if (rubricVersionPublicId == null || taskType == null) {
            return Optional.empty();
        }
        Key key = new Key(rubricVersionPublicId, taskType);
        Map<String, Integer> cached = maxima.get(key);
        if (cached != null) {
            return Optional.of(cached);
        }
        try {
            Map<String, Integer> read = maximaOf(resolver.resolve(rubricVersionPublicId, taskType));
            maxima.put(key, read);
            return Optional.of(read);
        } catch (DomainException unreadable) {
            return Optional.empty();
        }
    }

    private static Map<String, Integer> maximaOf(EffectiveRubric rubric) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (TraitDefinition trait : rubric.task().traits()) {
            if (!trait.scale().isPerWord()) {
                result.put(trait.key(), rubric.maxOf(trait, null));
            }
        }
        return Collections.unmodifiableMap(result);
    }
}
