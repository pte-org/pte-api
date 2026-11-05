package com.pte.scoring.internal.rubric.catalog;

import com.pte.scoring.internal.constant.RubricConstants;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.rubric.ObjectiveRule;
import com.pte.scoring.rubric.TaskRubric;

import java.util.List;
import java.util.Set;

/**
 * The twelve answer-key task types (Score Guide pp.40, 44). They have no traits: the rule is declared for display
 * and the executable scoring stays in the objective scorers.
 */
final class ObjectiveRubrics {

    private static final String READING_PAGES = "tr.40";
    private static final String LISTENING_PAGES = "tr.44";
    private static final String R = RubricConstants.SKILL_READING;
    private static final String L = RubricConstants.SKILL_LISTENING;
    private static final String W = RubricConstants.SKILL_WRITING;

    private ObjectiveRubrics() {
    }

    static List<TaskRubric> all() {
        return List.of(
                single(ScoringConstants.TASK_TYPE_MC_READING_SINGLE, Set.of(R), READING_PAGES),
                multiple(ScoringConstants.TASK_TYPE_MC_READING_MULTIPLE, Set.of(R), READING_PAGES),
                rule(ScoringConstants.TASK_TYPE_RE_ORDER_PARAGRAPHS, Set.of(R), READING_PAGES,
                        RubricConstants.OBJECTIVE_ADJACENT_PAIRS,
                        "Mỗi cặp đoạn văn liền kề được sắp đúng thứ tự được 1 điểm; tối thiểu 0."),
                perBlank(ScoringConstants.TASK_TYPE_FILL_IN_THE_BLANKS_DRAG_AND_DROP, Set.of(R), READING_PAGES),
                perBlank(ScoringConstants.TASK_TYPE_FILL_IN_THE_BLANKS_DROPDOWN, Set.of(R), READING_PAGES),
                single(ScoringConstants.TASK_TYPE_MC_LISTENING_SINGLE, Set.of(L), LISTENING_PAGES),
                multiple(ScoringConstants.TASK_TYPE_MC_LISTENING_MULTIPLE, Set.of(L), LISTENING_PAGES),
                perBlank(ScoringConstants.TASK_TYPE_FILL_IN_THE_BLANKS_TYPE_IN, Set.of(L), LISTENING_PAGES),
                single(ScoringConstants.TASK_TYPE_HIGHLIGHT_CORRECT_SUMMARY, Set.of(L, R), LISTENING_PAGES),
                single(ScoringConstants.TASK_TYPE_SELECT_MISSING_WORD, Set.of(L), LISTENING_PAGES),
                rule(ScoringConstants.TASK_TYPE_HIGHLIGHT_INCORRECT_WORDS, Set.of(L, R), LISTENING_PAGES,
                        RubricConstants.OBJECTIVE_PLUS_MINUS_FLOOR_ZERO,
                        "Mỗi từ chọn đúng +1 điểm, mỗi từ chọn sai -1 điểm; tối thiểu 0."),
                rule(ScoringConstants.TASK_TYPE_WRITE_FROM_DICTATION, Set.of(L, W), LISTENING_PAGES,
                        RubricConstants.OBJECTIVE_LCS_WORDS,
                        "Mỗi từ đúng chính tả được 1 điểm; từ sai hoặc viết sai chính tả 0 điểm."));
    }

    private static TaskRubric single(String type, Set<String> skills, String pages) {
        return rule(type, skills, pages, RubricConstants.OBJECTIVE_CORRECT_INCORRECT,
                "Đáp án đúng được 1 điểm, sai được 0 điểm.");
    }

    private static TaskRubric multiple(String type, Set<String> skills, String pages) {
        return rule(type, skills, pages, RubricConstants.OBJECTIVE_PLUS_MINUS_FLOOR_ZERO,
                "Mỗi lựa chọn đúng +1 điểm, mỗi lựa chọn sai -1 điểm; tối thiểu 0.");
    }

    private static TaskRubric perBlank(String type, Set<String> skills, String pages) {
        return rule(type, skills, pages, RubricConstants.OBJECTIVE_PER_BLANK,
                "Mỗi ô trống điền đúng được 1 điểm; tối thiểu 0.");
    }

    private static TaskRubric rule(String type, Set<String> skills, String pages, String code, String description) {
        return new TaskRubric(type, skills, List.of(), Set.of(), null,
                new ObjectiveRule(code, description, pages), pages);
    }
}
