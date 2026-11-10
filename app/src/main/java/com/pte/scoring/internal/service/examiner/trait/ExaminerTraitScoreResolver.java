package com.pte.scoring.internal.service.examiner.trait;

import com.pte.attempt.dto.response.SubmittedAnswerView;
import com.pte.scoring.CriteriaScores;
import com.pte.scoring.domain.AiEvaluation;
import com.pte.scoring.domain.ExaminerEvaluation;
import com.pte.scoring.dto.response.ExaminerRubricResponse;
import com.pte.scoring.internal.constant.ExaminerScoringConstants;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.exception.examiner.InvalidExaminerScoreException;
import com.pte.scoring.internal.rubric.service.ResolvedRubric;
import com.pte.scoring.internal.rubric.speech.ReadAloudContent;
import com.pte.scoring.internal.service.objective.AnswerContentReader;
import com.pte.scoring.internal.vendor.gemini.FormCheck;
import com.pte.scoring.rubric.*;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.util.*;

/** Human traits + Azure traits (machine band unless the examiner overrides it) + mechanical Form; RubricScorer alone produces the item score. */
@Service
public class ExaminerTraitScoreResolver {
    private final ExaminerTraitRubricReader rubrics;
    private final ExaminerTraitValidator validator;
    private final AnswerContentReader content;
    private final JsonMapper json;

    public ExaminerTraitScoreResolver(ExaminerTraitRubricReader rubrics, ExaminerTraitValidator validator,
                                     AnswerContentReader content, JsonMapper json) {
        this.rubrics = rubrics; this.validator = validator; this.content = content; this.json = json;
    }

    public ExaminerTraitScoreResult resolve(AiEvaluation answer, Map<String, BigDecimal> requested, Integer clientScore) {
        if (!isRubric(answer)) {
            if (requested != null && !requested.isEmpty()) throw invalid(ExaminerScoringConstants.TRAIT_UNKNOWN);
            return ExaminerTraitScoreResult.legacy(clientScore);
        }
        if (requested == null) throw invalid(ExaminerScoringConstants.TRAITS_REQUIRED);
        ResolvedRubric resolved = rubrics.read(answer);
        SubmittedAnswerView submission = content.one(answer.getAnswerPublicId(), answer.getTenantId()).orElse(null);
        Integer words = wordCount(answer, submission);
        Integer form = formBand(resolved.rubric(), submission);
        CriteriaScores ai = CriteriaScores.read(json, answer.getCriteriaScoresJson());
        boolean machineAvailable = answer.hasPublishableAiScore() && ai.isPearson()
                && resolved.versionPublicId().equals(ai.rubricVersion());
        Map<String, Integer> traits = validator.validate(resolved.rubric(), ExaminerTraitInputs.integers(requested),
                ai.traits(), machineAvailable, words, form);
        ScoreTrace trace = RubricScorer.score(resolved.rubric(), new TraitScoreSet(traits, words));
        if (clientScore != null && clientScore != trace.itemScore()) throw invalid(ExaminerScoringConstants.TRAIT_SCORE_MISMATCH);
        return new ExaminerTraitScoreResult(trace.itemScore(), trace, traits, resolved.versionPublicId(),
                storedJson(resolved.versionPublicId(), traits, words), words);
    }

    /** Replays/finalize use persisted filled traits, never a new Azure fallback or a different ACTIVE version. */
    public ExaminerTraitScoreResult stored(AiEvaluation answer, ExaminerEvaluation saved) {
        CriteriaScores traits = CriteriaScores.read(json, saved.getTraitScoresJson());
        if (!traits.isPearson() || saved.getRubricVersionPublicId() == null) {
            if (isRubric(answer) && !saved.isSubmitted()) throw invalid(ExaminerScoringConstants.TRAITS_REQUIRED);
            return ExaminerTraitScoreResult.legacy(saved.getScore());
        }
        if (!Objects.equals(saved.getRubricVersionPublicId(), traits.rubricVersion())) throw invalid(ExaminerScoringConstants.RUBRIC_MISMATCH);
        ResolvedRubric resolved = rubrics.stored(saved.getRubricVersionPublicId(), answer.getTaskType());
        Integer words = storedWords(saved.getTraitScoresJson());
        if (words == null) words = wordCount(answer, null);
        ScoreTrace trace = RubricScorer.score(resolved.rubric(), new TraitScoreSet(traits.traits(), words));
        if (!Objects.equals(saved.getScore(), trace.itemScore())) throw invalid(ExaminerScoringConstants.TRAIT_SCORE_MISMATCH);
        return new ExaminerTraitScoreResult(trace.itemScore(), trace, traits.traits(), resolved.versionPublicId(), saved.getTraitScoresJson(), words);
    }

    public UUID versionFor(AiEvaluation answer) { return isRubric(answer) ? rubrics.read(answer).versionPublicId() : null; }

    public boolean matchesAi(AiEvaluation answer, ExaminerTraitScoreResult result) {
        if (result.rubricVersionPublicId() == null) return true;
        CriteriaScores ai = CriteriaScores.read(json, answer.getCriteriaScoresJson());
        return ai.isPearson() && Objects.equals(result.rubricVersionPublicId(), ai.rubricVersion()) && ai.traits().equals(result.traits());
    }

    public Map<UUID, ExaminerRubricResponse> schemas(List<AiEvaluation> answers, Map<UUID, SubmittedAnswerView> submissions) {
        List<AiEvaluation> scored = answers.stream().filter(ExaminerTraitScoreResolver::isRubric).toList();
        var resolved = rubrics.readMany(scored); Map<UUID, ExaminerRubricResponse> result = new LinkedHashMap<>();
        for (AiEvaluation answer : scored) {
            var value = resolved.get(answer.getAnswerPublicId());
            var submission = submissions.get(answer.getAnswerPublicId());
            Integer words = wordCount(answer, submission);
            Integer form = formBand(value.rubric(), submission);
            var traits = value.rubric().task().traits().stream().map(trait -> new ExaminerRubricResponse.Trait(trait.key(),
                    trait.displayNameVi(), trait.displayNameEn(), value.rubric().maxOf(trait, words), trait.source().name(),
                    !RubricTraits.FORM.equals(trait.key()), trait.source() == TraitSource.AZURE,
                    trait.levels().stream().map(level -> new ExaminerRubricResponse.Level(level.level(), level.text())).toList())).toList();
            result.put(answer.getAnswerPublicId(), new ExaminerRubricResponse(value.versionPublicId(), answer.getTaskType(), traits,
                    form == null ? Map.of() : Map.of(RubricTraits.FORM, form)));
        }
        return result;
    }

    private String storedJson(UUID version, Map<String, Integer> traits, Integer words) {
        var root = (tools.jackson.databind.node.ObjectNode) json.readTree(CriteriaScores.pearson(version, traits).toJson(json));
        if (words != null) root.put(ScoringConstants.DETAIL_WORD_COUNT, words);
        return json.writeValueAsString(root);
    }
    private Integer storedWords(String stored) {
        try {
            var count = json.readTree(stored).get(ScoringConstants.DETAIL_WORD_COUNT);
            return count != null && count.isIntegralNumber() && count.intValue() > 0 ? count.intValue() : null;
        } catch (RuntimeException malformed) { return null; }
    }
    private Integer wordCount(AiEvaluation answer, SubmittedAnswerView submission) {
        if (!ScoringConstants.TASK_TYPE_READ_ALOUD.equals(answer.getTaskType())) return null;
        if (submission != null && submission.promptText() != null) return passageWords(submission.promptText());
        Integer stored = storedWords(answer.getDetailJson());
        if (stored != null) return stored;
        var loaded = content.one(answer.getAnswerPublicId(), answer.getTenantId()).orElseThrow(() -> invalid(ExaminerScoringConstants.ANSWER_CONTENT_UNAVAILABLE));
        return passageWords(loaded.promptText());
    }
    private static int passageWords(String prompt) {
        try { return ReadAloudContent.measure(prompt, "").wordCount(); }
        catch (com.pte.scoring.internal.vendor.AiProviderException invalid) {
            throw invalid(ExaminerScoringConstants.ANSWER_CONTENT_UNAVAILABLE);
        }
    }
    private static Integer formBand(EffectiveRubric rubric, SubmittedAnswerView submission) {
        if (rubric.effectiveFormRule() == null) return null;
        if (submission == null) throw invalid(ExaminerScoringConstants.ANSWER_CONTENT_UNAVAILABLE);
        return rubric.effectiveFormRule().bandFor(FormCheck.measure(submission.payload()));
    }
    private static boolean isRubric(AiEvaluation answer) { return com.pte.scoring.internal.constant.ExaminerScoringConstants.TRAIT_TASK_TYPES.contains(answer.getTaskType()); }
    private static InvalidExaminerScoreException invalid(String code) { return new InvalidExaminerScoreException(code); }
}
