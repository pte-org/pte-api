package com.pte.itembank;

import com.pte.itembank.domain.Question;
import com.pte.itembank.domain.QuestionOption;
import com.pte.itembank.domain.enums.QuestionPool;
import com.pte.itembank.domain.enums.QuestionStatus;
import com.pte.itembank.domain.enums.Visibility;
import com.pte.itembank.internal.repository.QuestionRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticeQuestionServiceTest {

    private final QuestionRepository repository = mock(QuestionRepository.class);
    private final PracticeQuestionService service = new PracticeQuestionService(repository);

    @Test
    void returnsOnlyStudentSafeQuestionContent() {
        UUID publicId = UUID.randomUUID();
        Question question = new Question();
        question.setPublicId(publicId);
        question.setTaskTypeKey("MC_READING_SINGLE");
        question.setPromptText("Read the passage.");
        question.setReferenceAnswerText("private reference");
        question.setCorrectAnswerText("private answer");
        QuestionOption option = new QuestionOption();
        option.setText("The supported statement");
        option.setCorrect(true);
        option.setOrderIndex(2);
        question.addOption(option);
        when(repository.findPublishedPracticeQuestionIdsByTaskTypeKey(eq("MC_READING_SINGLE"), any()))
                .thenReturn(List.of(publicId));
        when(repository.findWithOptionsByPublicId(publicId)).thenReturn(java.util.Optional.of(question));

        var content = service.findPublishedQuestion("mc_reading_single").orElseThrow();

        assertThat(content.publicId()).isEqualTo(publicId);
        assertThat(content.promptText()).isEqualTo("Read the passage.");
        assertThat(content.options()).singleElement().satisfies(item -> {
            assertThat(item.text()).isEqualTo("The supported statement");
            assertThat(item.orderIndex()).isEqualTo(2);
        });
        assertThat(content.toString()).doesNotContain("private reference", "private answer", "true");
    }

    @Test
    void checksOnlyThePublishedPracticePool() {
        when(repository.existsByDeletedFalseAndVisibilityAndStatusAndCurrentTrueAndPoolAndTaskTypeKey(
                Visibility.SHARED, QuestionStatus.APPROVED, QuestionPool.PRACTICE, "READ_ALOUD"))
                .thenReturn(true);

        assertThat(service.hasPublishedQuestion("read_aloud")).isTrue();
        verify(repository).existsByDeletedFalseAndVisibilityAndStatusAndCurrentTrueAndPoolAndTaskTypeKey(
                Visibility.SHARED, QuestionStatus.APPROVED, QuestionPool.PRACTICE, "READ_ALOUD");
    }
}
