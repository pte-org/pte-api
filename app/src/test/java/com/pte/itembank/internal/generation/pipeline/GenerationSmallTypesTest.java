package com.pte.itembank.internal.generation.pipeline;

import com.pte.itembank.domain.enums.CefrLevel;
import com.pte.itembank.domain.enums.QuestionPool;
import com.pte.itembank.dto.request.CreateQuestionRequest;
import com.pte.itembank.dto.request.OptionRequest;
import com.pte.itembank.internal.generation.exception.GenerationCallBudgetExceededException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GenerationSmallTypesTest {

    @Test
    void theHashIgnoresCaseAccentFreePunctuationAndSpacing() {
        String first = ContentHasher.hash("The  Committee, reviewed the proposal!");
        String second = ContentHasher.hash("the committee reviewed   the proposal");

        assertThat(first).isEqualTo(second).hasSize(64);
    }

    @Test
    void differentContentGivesADifferentHash() {
        assertThat(ContentHasher.hash("alpha beta")).isNotEqualTo(ContentHasher.hash("alpha gamma"));
    }

    @Test
    void normalizeKeepsLettersAndDigitsOnly() {
        assertThat(ContentHasher.normalize("  A-B, C!  9 ")).isEqualTo("ab c 9");
    }

    @Test
    void theBudgetAllowsExactlyTheConfiguredCalls() {
        GenerationCallBudget budget = new GenerationCallBudget(2);

        budget.consume();
        budget.consume();

        assertThat(budget.remaining()).isZero();
        assertThatThrownBy(budget::consume).isInstanceOf(GenerationCallBudgetExceededException.class);
    }

    @Test
    void theStubOnlySupportsMultipleChoiceReadingSingle() {
        StubItemGenerator stub = new StubItemGenerator();

        assertThat(stub.supports("MC_READING_SINGLE")).isTrue();
        assertThat(stub.supports("WRITE_ESSAY")).isFalse();
    }

    @Test
    void theStubBuildsAValidShapedQuestionThatIsUniquePerSlot() {
        StubItemGenerator stub = new StubItemGenerator();
        UUID job = UUID.randomUUID();

        GeneratedItem first = stub.generate(request(job, 0), new GenerationCallBudget(1));
        GeneratedItem second = stub.generate(request(job, 1), new GenerationCallBudget(1));

        CreateQuestionRequest question = first.question();
        assertThat(question.taskTypeKey()).isEqualTo("MC_READING_SINGLE");
        assertThat(question.cefrLevel()).isEqualTo("B2");
        assertThat(question.pool()).isEqualTo("PRACTICE");
        assertThat(question.promptText()).contains(first.stimulusText());
        assertThat(question.options()).hasSize(4).extracting(OptionRequest::orderIndex)
                .containsExactly(0, 1, 2, 3);
        assertThat(question.options()).filteredOn(OptionRequest::correct).hasSize(1);
        assertThat(ContentHasher.hash(first.stimulusText())).isNotEqualTo(ContentHasher.hash(second.stimulusText()));
        assertThat(first.source()).isEqualTo("stub");
    }

    private static GenerationRequest request(UUID job, int index) {
        return new GenerationRequest(job, index, "MC_READING_SINGLE", CefrLevel.B2, QuestionPool.PRACTICE, null);
    }
}
