package com.pte.scoring.internal.vendor.gemini.scorer;

import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.rubric.prompt.RubricPromptBuilder;
import com.pte.scoring.rubric.TaskRubric;

import java.util.Map;
import java.util.Optional;

/**
 * Read access to the fixed wording the Gemini scorers put around the rubric, for the AI admin pages. The scorers
 * keep the text; this only exposes it, so the page shows what the model is really sent.
 */
public final class GeminiPromptTexts {

    private static final String SECTION_BREAK = "\n\n";

    private static final Map<String, String> EXAMINERS = Map.of(
            ScoringConstants.TASK_TYPE_DESCRIBE_IMAGE, DescribeImageScorer.EXAMINER,
            ScoringConstants.TASK_TYPE_RESPOND_TO_A_SITUATION, RespondToSituationScorer.EXAMINER,
            ScoringConstants.TASK_TYPE_RE_TELL_LECTURE, RetellLectureScorer.EXAMINER,
            ScoringConstants.TASK_TYPE_SUMMARIZE_GROUP_DISCUSSION, SummarizeGroupDiscussionScorer.EXAMINER,
            ScoringConstants.TASK_TYPE_SUMMARIZE_SPOKEN_TEXT, SummarizeSpokenTextScorer.EXAMINER,
            ScoringConstants.TASK_TYPE_SUMMARIZE_WRITTEN_TEXT, SummarizeWrittenTextScorer.EXAMINER,
            ScoringConstants.TASK_TYPE_WRITE_ESSAY, WriteEssayScorer.EXAMINER);

    private GeminiPromptTexts() {
    }

    /** The examiner role a task's Gemini scorer starts its system prompt with, if the task has such a scorer. */
    public static Optional<String> examiner(String taskType) {
        return Optional.ofNullable(EXAMINERS.get(taskType));
    }

    /** The reminder that the candidate's text is data and never an instruction. */
    public static String guard() {
        return GeminiScorers.GUARD;
    }

    /** The system prompt as the scorer builds it: examiner role, the rubric section, then the guard. */
    public static Optional<String> systemPrompt(String taskType, TaskRubric task) {
        return examiner(taskType).map(examiner -> GeminiScorers.system(
                examiner + SECTION_BREAK + RubricPromptBuilder.rubricSection(task)));
    }
}
