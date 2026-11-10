package com.pte.scoring.internal.service.examiner.trait;

import com.pte.assessment.ExamDefinitionService;
import com.pte.scoring.domain.AiEvaluation;
import com.pte.scoring.domain.ScoringRubricVersion;
import com.pte.scoring.domain.ScoringRubricVersion.RubricVersionStatus;
import com.pte.scoring.internal.constant.ExaminerScoringConstants;
import com.pte.scoring.internal.exception.examiner.InvalidExaminerScoreException;
import com.pte.scoring.internal.rubric.repository.ScoringRubricVersionRepository;
import com.pte.scoring.internal.rubric.service.ResolvedRubric;
import com.pte.scoring.internal.rubric.service.RubricResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Per-call caches avoid per-answer definition/version queries in the assigned-attempt detail. */
@Service
public class ExaminerTraitRubricReader {
    private final ExamDefinitionService definitions;
    private final ScoringRubricVersionRepository versions;
    private final RubricResolver resolver;

    public ExaminerTraitRubricReader(ExamDefinitionService definitions, ScoringRubricVersionRepository versions, RubricResolver resolver) {
        this.definitions = definitions; this.versions = versions; this.resolver = resolver;
    }
    @Transactional(readOnly = true)
    public ResolvedRubric read(AiEvaluation answer) { return new ReadContext().read(answer); }

    @Transactional(readOnly = true)
    public Map<UUID, ResolvedRubric> readMany(List<AiEvaluation> answers) {
        var context = new ReadContext(); Map<UUID, ResolvedRubric> result = new LinkedHashMap<>();
        answers.forEach(answer -> result.put(answer.getAnswerPublicId(), context.read(answer)));
        return result;
    }
    @Transactional(readOnly = true)
    public ResolvedRubric stored(UUID id, String task) {
        var version = readable(versions.findByPublicId(id).orElseThrow(ExaminerTraitRubricReader::unreadable));
        return new ResolvedRubric(id, resolver.effectiveFor(version, task));
    }
    private static ScoringRubricVersion readable(ScoringRubricVersion row) {
        if (!row.isImmutable()) throw unreadable();
        return row;
    }
    private static InvalidExaminerScoreException unreadable() { return new InvalidExaminerScoreException(ExaminerScoringConstants.RUBRIC_NOT_READABLE); }

    private final class ReadContext {
        private final Map<UUID, UUID> pins = new HashMap<>();
        private final Map<UUID, ScoringRubricVersion> rows = new HashMap<>();
        private final Map<String, ResolvedRubric> tasks = new HashMap<>();
        private ScoringRubricVersion active;
        ResolvedRubric read(AiEvaluation answer) {
            UUID pin = answer.getExamDefinitionPublicId() == null ? null
                    : pins.computeIfAbsent(answer.getExamDefinitionPublicId(), id -> definitions.getByPublicId(id).rubricVersionPublicId());
            if (pin == null) pin = answer.getRubricVersionPublicId();
            ScoringRubricVersion version;
            if (pin == null) {
                if (active == null) active = readable(versions.findByStatus(RubricVersionStatus.ACTIVE).orElseThrow(ExaminerTraitRubricReader::unreadable));
                version = active;
            } else version = rows.computeIfAbsent(pin, id -> readable(versions.findByPublicId(id).orElseThrow(ExaminerTraitRubricReader::unreadable)));
            if (answer.getRubricVersionPublicId() != null && !answer.getRubricVersionPublicId().equals(version.getPublicId()))
                throw new InvalidExaminerScoreException(ExaminerScoringConstants.RUBRIC_MISMATCH);
            UUID id = version.getPublicId();
            return tasks.computeIfAbsent(id + ":" + answer.getTaskType(), key -> new ResolvedRubric(id, resolver.effectiveFor(version, answer.getTaskType())));
        }
    }
}
