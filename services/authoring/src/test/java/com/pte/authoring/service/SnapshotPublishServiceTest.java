package com.pte.authoring.service;

import com.pte.authoring.domain.Question;
import com.pte.authoring.domain.QuestionOption;
import com.pte.authoring.domain.enums.PteTaskType;
import com.pte.authoring.domain.enums.Visibility;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers Phase 8's `deliveryOrder` fix: `Question.options` is JPA
 * `@OrderBy("orderIndex ASC")`, so a real fetch always returns options
 * already sorted ascending by their orderIndex identity. For
 * RE_ORDER_PARAGRAPHS specifically, orderIndex is also the CORRECT final
 * position, so serving the natural order would deliver an
 * already-correctly-ordered task — `deliveryOrder` must rotate it so every
 * option lands at a different array index than its orderIndex identity.
 */
class SnapshotPublishServiceTest {

    // deliveryOrder() touches no injected dependency — safe to construct
    // with nulls for a focused unit test of this one pure function.
    private final SnapshotPublishService service =
            new SnapshotPublishService(null, null, null, null, null, null);

    private Question questionWithOptions(PteTaskType taskType, int optionCount) {
        Question question = new Question();
        question.setPteTaskType(taskType);
        question.setVisibility(Visibility.SHARED);
        question.setTitle("test");
        // Simulates Hibernate's @OrderBy("orderIndex ASC") — options arrive
        // already ascending, exactly as a real fetch would return them.
        for (int i = 0; i < optionCount; i++) {
            QuestionOption option = new QuestionOption();
            option.setText("option-" + i);
            option.setOrderIndex(i);
            option.setCorrect(false);
            question.addOption(option);
        }
        return question;
    }

    @Test
    void reOrderParagraphs_fourOptions_everyOptionMovesToADifferentIndexThanItsOrderIndex() {
        Question question = questionWithOptions(PteTaskType.RE_ORDER_PARAGRAPHS, 4);

        List<QuestionOption> delivered = service.deliveryOrder(question);

        assertThat(delivered).hasSize(4);
        for (int arrayIndex = 0; arrayIndex < delivered.size(); arrayIndex++) {
            assertThat(delivered.get(arrayIndex).getOrderIndex())
                    .as("array index %d must not hold the option whose orderIndex identity equals that index", arrayIndex)
                    .isNotEqualTo(arrayIndex);
        }
    }

    @Test
    void reOrderParagraphs_fourOptions_isARotationNotADrop_allFourOrderIndexesStillPresent() {
        Question question = questionWithOptions(PteTaskType.RE_ORDER_PARAGRAPHS, 4);

        List<QuestionOption> delivered = service.deliveryOrder(question);

        assertThat(delivered.stream().map(QuestionOption::getOrderIndex)).containsExactlyInAnyOrder(0, 1, 2, 3);
    }

    @Test
    void reOrderParagraphs_singleOption_returnedUnchanged_noRotationOfATrivialList() {
        Question question = questionWithOptions(PteTaskType.RE_ORDER_PARAGRAPHS, 1);

        List<QuestionOption> delivered = service.deliveryOrder(question);

        assertThat(delivered.get(0).getOrderIndex()).isEqualTo(0);
    }

    @Test
    void nonReorderTaskType_naturalOrderPreserved_notRotated() {
        Question question = questionWithOptions(PteTaskType.MC_READING_MULTIPLE, 4);

        List<QuestionOption> delivered = service.deliveryOrder(question);

        assertThat(delivered.stream().map(QuestionOption::getOrderIndex)).containsExactly(0, 1, 2, 3);
    }
}
