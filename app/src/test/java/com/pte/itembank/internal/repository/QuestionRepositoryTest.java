package com.pte.itembank.internal.repository;

import com.pte.itembank.domain.Question;
import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.itembank.domain.enums.QuestionPool;
import com.pte.itembank.domain.enums.QuestionStatus;
import com.pte.itembank.domain.enums.Visibility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Set;
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
                null, "READING", QuestionStatus.APPROVED, null, "packet", null, PageRequest.of(0, 10));
        Page<UUID> byPublicId = questionRepository.findPagePublicIds(
                null, null, null, null, matching.getPublicId().toString(), matching.getPublicId(),
                PageRequest.of(0, 10));

        assertThat(byText.getContent()).containsExactly(matching.getPublicId());
        assertThat(byText.getTotalElements()).isEqualTo(1);
        assertThat(byPublicId.getContent()).containsExactly(matching.getPublicId());
    }

    @Test
    void groupedCounts_coverSharedQuestionBankOnly() {
        persist("Shared listening", "prompt", "LISTENING", QuestionStatus.APPROVED);
        persist("Shared draft", "prompt", "LISTENING", QuestionStatus.DRAFT);
        Question deletedQuestion = persist("Deleted reading", "prompt", "READING", QuestionStatus.APPROVED);
        deletedQuestion.setDeleted(true);
        questionRepository.saveAndFlush(deletedQuestion);

        assertThat(questionRepository.countByDeletedFalseAndVisibility(Visibility.SHARED)).isEqualTo(2);
        assertThat(questionRepository.countBySectionAndVisibility(Visibility.SHARED))
                .containsExactlyInAnyOrder(
                        new Object[] { "LISTENING", 2L });
    }

    @Test
    void pageQuery_filtersByPool() {
        Question exam = persist("Exam item", "prompt", "READING", QuestionStatus.APPROVED);
        Question practice = persist("Practice item", "prompt", "READING", QuestionStatus.APPROVED,
                QuestionPool.PRACTICE);

        Page<UUID> practiceOnly = questionRepository.findPagePublicIds(
                null, null, null, QuestionPool.PRACTICE, "", null, PageRequest.of(0, 10));
        Page<UUID> examOnly = questionRepository.findPagePublicIds(
                null, null, null, QuestionPool.EXAM, "", null, PageRequest.of(0, 10));
        Page<UUID> both = questionRepository.findPagePublicIds(
                null, null, null, null, "", null, PageRequest.of(0, 10));

        assertThat(practiceOnly.getContent()).containsExactly(practice.getPublicId());
        assertThat(examOnly.getContent()).containsExactly(exam.getPublicId());
        assertThat(both.getContent()).containsExactlyInAnyOrder(exam.getPublicId(), practice.getPublicId());
    }

    @Test
    void examGenerationQueries_ignorePracticeQuestions() {
        persist("Exam item", "prompt", "READING", QuestionStatus.APPROVED);
        persist("Practice item", "prompt", "READING", QuestionStatus.APPROVED, QuestionPool.PRACTICE);
        String key = PteTaskType.MC_READING_SINGLE.name();

        // Count projections only: H2 cannot convert native UUID result lists, Postgres can.
        assertThat(questionRepository.countPublishedSharedGroupedByTaskTypeKey(Set.of(key)))
                .singleElement()
                .satisfies(row -> assertThat(row.getCount()).isEqualTo(1L));
        assertThat(questionRepository.countPublishedSharedGroupedByTaskType(Set.of(key)))
                .singleElement()
                .satisfies(row -> assertThat(row.getCount()).isEqualTo(1L));
    }

    private Question persist(String title, String prompt, String section, QuestionStatus status) {
        return persist(title, prompt, section, status, QuestionPool.EXAM);
    }

    private Question persist(String title, String prompt, String section, QuestionStatus status,
            QuestionPool pool) {
        Question question = new Question();
        question.setPteTaskType(section.equals("READING") ? PteTaskType.MC_READING_SINGLE : PteTaskType.READ_ALOUD);
        question.setTaskTypeKey(question.getPteTaskType().name());
        question.setTaskTypeSection(section);
        question.setVisibility(Visibility.SHARED);
        question.setPool(pool);
        question.setStatus(status);
        question.setRevisionGroupPublicId(UUID.randomUUID());
        question.setRevisionNumber(1);
        question.setCurrent(true);
        question.setTitle(title);
        question.setPromptText(prompt);
        return questionRepository.saveAndFlush(question);
    }
}
