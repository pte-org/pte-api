package com.pte.itembank.internal.repository;

import com.pte.itembank.domain.Question;
import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.itembank.domain.enums.QuestionStatus;
import com.pte.itembank.domain.enums.Visibility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class QuestionRepositoryTest {

    @Autowired
    private QuestionRepository questionRepository;

    @Test
    void pageQuery_filtersBeforePaginationAndSupportsPublicIdSearch() {
        Question matching = persist("Packet switching", "A network packet carries data", "READING",
                QuestionStatus.APPROVED);
        persist("Other title", "Unrelated prompt", "READING", QuestionStatus.APPROVED);
        persist("Draft title", "A network packet is still a draft", "READING", QuestionStatus.DRAFT);

        Page<UUID> byText = questionRepository.findPagePublicIds(
                null, "READING", QuestionStatus.APPROVED, "packet", null, PageRequest.of(0, 10));
        Page<UUID> byPublicId = questionRepository.findPagePublicIds(
                null, null, null, matching.getPublicId().toString(), matching.getPublicId(),
                PageRequest.of(0, 10));

        assertThat(byText.getContent()).containsExactly(matching.getPublicId());
        assertThat(byText.getTotalElements()).isEqualTo(1);
        assertThat(byPublicId.getContent()).containsExactly(matching.getPublicId());
    }

    @Test
    void groupedCounts_coverSharedQuestionBankOnly() {
        persist("Shared listening", "prompt", "LISTENING", QuestionStatus.APPROVED);
        persist("Shared draft", "prompt", "LISTENING", QuestionStatus.DRAFT);
        Question privateQuestion = persist("Private reading", "prompt", "READING", QuestionStatus.APPROVED);
        privateQuestion.setVisibility(Visibility.PRIVATE);
        questionRepository.saveAndFlush(privateQuestion);

        assertThat(questionRepository.countByDeletedFalseAndVisibility(Visibility.SHARED)).isEqualTo(2);
        assertThat(questionRepository.countBySectionAndVisibility(Visibility.SHARED))
                .containsExactlyInAnyOrder(
                        new Object[] { "LISTENING", 2L });
    }

    private Question persist(String title, String prompt, String section, QuestionStatus status) {
        Question question = new Question();
        question.setPteTaskType(section.equals("READING") ? PteTaskType.MC_READING_SINGLE : PteTaskType.READ_ALOUD);
        question.setTaskTypeKey(question.getPteTaskType().name());
        question.setTaskTypeSection(section);
        question.setVisibility(Visibility.SHARED);
        question.setStatus(status);
        question.setRevisionGroupPublicId(UUID.randomUUID());
        question.setRevisionNumber(1);
        question.setCurrent(true);
        question.setTitle(title);
        question.setPromptText(prompt);
        return questionRepository.saveAndFlush(question);
    }
}
