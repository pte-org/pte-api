package com.pte.scoring.internal.service.objective;

import com.pte.itembank.TaskRuntimeProfileDescriptor;
import com.pte.attempt.dto.response.SubmittedAnswerView;
import com.pte.scoring.domain.enums.ScoringMethod;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.exception.UnsupportedTaskTypeException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * Rule-based scoring for objective task types: the 5 PTE Reading types plus
 * the four option-based Listening types ({@code MC_LISTENING_SINGLE}, {@code
 * MC_LISTENING_MULTIPLE}, {@code HIGHLIGHT_CORRECT_SUMMARY}, and {@code
 * SELECT_MISSING_WORD}), plus the reference-based {@code
 * FILL_IN_THE_BLANKS_TYPE_IN}, {@code HIGHLIGHT_INCORRECT_WORDS}, and {@code
 * WRITE_FROM_DICTATION} types. Since Phase 4 (plans/score-template-exam-generation),
 * WHICH task types these are is the exam item's decision
 * (via the runtime contract frozen on the exam item) — {@link #supports} is a trivial gate
 * on the already-resolved {@link ScoringMethod}, not a second hardcoded set;
 * {@link #score}'s per-task-type algorithm switch is unchanged.
 */
@Service
public class ObjectiveScoringService {

    private final OptionAnswerScorer optionScorer;
    private final ReferenceAnswerScorer referenceScorer;

    public ObjectiveScoringService(JsonMapper jsonMapper) {
        this.optionScorer = new OptionAnswerScorer(jsonMapper);
        this.referenceScorer = new ReferenceAnswerScorer(jsonMapper);
    }

    public boolean supports(ScoringMethod scoringMethod) {
        return scoringMethod == ScoringMethod.OBJECTIVE;
    }

    /**
     * @return raw score on a 0–100 PERCENTAGE scale (100 correct, 0 incorrect)
     * — NOT 0/1. AI scoring (also 0–100) must combine with objective scores in
     * reporting's aggregation formula (percentCorrect = average of
     * contributing rawScores / 100), so every scorer must share one scale.
     */
    public int score(SubmittedAnswerView answer) {
        TaskRuntimeProfileDescriptor runtime = answer.runtime();
        String route = runtime == null ? answer.taskType() : runtime.rendererKey();
        return switch (route) {
            case ScoringConstants.TASK_TYPE_MC_READING_SINGLE -> optionScorer.scoreSingleChoice(answer);
            case ScoringConstants.TASK_TYPE_MC_READING_MULTIPLE -> optionScorer.scoreMultipleChoice(answer);
            case ScoringConstants.TASK_TYPE_MC_LISTENING_SINGLE,
                    ScoringConstants.TASK_TYPE_HIGHLIGHT_CORRECT_SUMMARY,
                    ScoringConstants.TASK_TYPE_SELECT_MISSING_WORD -> optionScorer.scoreSingleChoice(answer);
            case ScoringConstants.TASK_TYPE_MC_LISTENING_MULTIPLE -> optionScorer.scoreMultipleChoice(answer);
            case ScoringConstants.TASK_TYPE_RE_ORDER_PARAGRAPHS -> optionScorer.scoreReorderParagraphs(answer);
            case ScoringConstants.TASK_TYPE_FILL_IN_THE_BLANKS_DRAG_AND_DROP, ScoringConstants.TASK_TYPE_FILL_IN_THE_BLANKS_DROPDOWN ->
                    optionScorer.scoreFillBlanks(answer);
            case ScoringConstants.TASK_TYPE_FILL_IN_THE_BLANKS_TYPE_IN -> referenceScorer.scoreListeningFillBlanks(answer);
            case ScoringConstants.TASK_TYPE_HIGHLIGHT_INCORRECT_WORDS -> referenceScorer.scoreHighlightIncorrectWords(answer);
            case ScoringConstants.TASK_TYPE_WRITE_FROM_DICTATION -> referenceScorer.scoreWriteFromDictation(answer);
            case "MC_READING_SINGLE_V1", "MC_LISTENING_SINGLE_V1", "HIGHLIGHT_CORRECT_SUMMARY_V1",
                    "SELECT_MISSING_WORD_V1" -> optionScorer.scoreSingleChoice(answer);
            case "MC_READING_MULTIPLE_V1", "MC_LISTENING_MULTIPLE_V1" -> optionScorer.scoreMultipleChoice(answer);
            case "RE_ORDER_PARAGRAPHS_V1" -> optionScorer.scoreReorderParagraphs(answer);
            case "FILL_IN_THE_BLANKS_DRAG_AND_DROP_V1", "FILL_IN_THE_BLANKS_DROPDOWN_V1" ->
                    optionScorer.scoreFillBlanks(answer);
            case "FILL_IN_THE_BLANKS_TYPE_IN_V1" -> referenceScorer.scoreListeningFillBlanks(answer);
            case "HIGHLIGHT_INCORRECT_WORDS_V1" -> referenceScorer.scoreHighlightIncorrectWords(answer);
            case "WRITE_FROM_DICTATION_V1" -> referenceScorer.scoreWriteFromDictation(answer);
            default -> throw new UnsupportedTaskTypeException();
        };
    }
}
