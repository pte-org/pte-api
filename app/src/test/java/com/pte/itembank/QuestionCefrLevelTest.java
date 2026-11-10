package com.pte.itembank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.pte.itembank.domain.Question;
import com.pte.itembank.domain.enums.CefrLevel;
import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.itembank.domain.enums.QuestionStatus;
import com.pte.itembank.dto.request.CreateQuestionRequest;
import com.pte.itembank.dto.request.UpdateQuestionRequest;
import com.pte.itembank.dto.response.QuestionResponse;
import com.pte.itembank.internal.exception.QuestionValidationException;
import com.pte.itembank.internal.repository.QuestionRepository;
import com.pte.itembank.internal.service.ItembankAccessPolicy;
import com.pte.itembank.internal.service.QuestionValidationHelper;
import com.pte.shared.security.CurrentUser;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuestionCefrLevelTest {

    @Mock
    private QuestionRepository questionRepository;

    private ItembankService service;
    private CurrentUser author;

    @BeforeEach
    void setUp() {
        service = ItembankFixtures.itembankService(questionRepository, new QuestionValidationHelper(null),
                new ItembankAccessPolicy());
        author = new CurrentUser(UUID.randomUUID(), null, List.of("ACADEMIC_STAFF"));
    }

    private CreateQuestionRequest create(String cefrLevel) {
        return new CreateQuestionRequest("READ_ALOUD", "READ_ALOUD", "prompt", null, null, null, null, null, null,
                null, "EXAM", null, null, cefrLevel);
    }

    private Question draft(boolean forExam, CefrLevel level) {
        Question question = new Question();
        question.setPteTaskType(PteTaskType.READ_ALOUD);
        question.setTaskTypeKey("READ_ALOUD");
        question.setTaskTypeSection("SPEAKING");
        question.setPromptText("prompt");
        question.setStatus(QuestionStatus.DRAFT);
        question.setAuthorUserPublicId(author.userId());
        question.setAvailableForExam(forExam);
        question.setCefrLevel(level);
        return question;
    }

    @Test
    void parsesALevelCaseInsensitively() {
        assertThat(CefrLevel.parseOrNull(" b2 ")).isEqualTo(CefrLevel.B2);
        assertThat(CefrLevel.parseOrNull(null)).isNull();
        assertThat(CefrLevel.parseOrNull("  ")).isNull();
    }

    @Test
    void createStoresTheLevelAndReturnsIt() {
        when(questionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        QuestionResponse response = service.create(create("c1"), author);

        assertThat(response.cefrLevel()).isEqualTo("C1");
    }

    @Test
    void createRejectsALevelOutsideTheScale() {
        assertThatThrownBy(() -> service.create(create("D9"), author))
                .isInstanceOf(QuestionValidationException.class);
    }

    @Test
    void createWithoutALevelLeavesItUnclassified() {
        when(questionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.create(create(null), author).cefrLevel()).isNull();
    }

    @Test
    void anExamQuestionWithoutALevelCannotBeSubmittedForApproval() {
        UUID id = UUID.randomUUID();
        when(questionRepository.findWithOptionsByPublicId(id)).thenReturn(Optional.of(draft(true, null)));

        assertThatThrownBy(() -> service.submitApproval(id, author)).isInstanceOf(QuestionValidationException.class);
    }

    @Test
    void anExamQuestionWithALevelCanBeSubmitted() {
        UUID id = UUID.randomUUID();
        Question question = draft(true, CefrLevel.B2);
        when(questionRepository.findWithOptionsByPublicId(id)).thenReturn(Optional.of(question));

        service.submitApproval(id, author);

        assertThat(question.getStatus()).isEqualTo(QuestionStatus.PENDING_APPROVAL);
    }

    @Test
    void aPracticeOnlyQuestionNeedsNoLevelToBeSubmitted() {
        UUID id = UUID.randomUUID();
        Question question = draft(false, null);
        when(questionRepository.findWithOptionsByPublicId(id)).thenReturn(Optional.of(question));

        service.submitApproval(id, author);

        assertThat(question.getStatus()).isEqualTo(QuestionStatus.PENDING_APPROVAL);
    }

    @Test
    void updateKeepsTheLevelWhenTheRequestOmitsItAndChangesItWhenGiven() {
        UUID id = UUID.randomUUID();
        Question question = draft(true, CefrLevel.B1);
        when(questionRepository.findWithOptionsByPublicId(id)).thenReturn(Optional.of(question));

        service.update(id, new UpdateQuestionRequest("new prompt", null, null, null, null, null, null, null, null,
                null, null), author);
        assertThat(question.getCefrLevel()).isEqualTo(CefrLevel.B1);

        service.update(id, new UpdateQuestionRequest("new prompt", null, null, null, null, null, null, null, null,
                null, null, "C2"), author);
        assertThat(question.getCefrLevel()).isEqualTo(CefrLevel.C2);
    }
}
