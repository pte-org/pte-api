package com.pte.itembank.internal.repository;

import com.pte.itembank.domain.Question;
import com.pte.itembank.domain.QuestionOption;
import com.pte.itembank.domain.enums.CefrLevel;
import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.itembank.domain.enums.QuestionPool;
import com.pte.itembank.domain.enums.QuestionStatus;
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

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Test
    void pageQuery_filtersBeforePaginationAndSupportsPublicIdSearch() {
        Question matching = persist("A network packet carries data", "READING",
                QuestionStatus.APPROVED);
        persist("Unrelated prompt", "READING", QuestionStatus.APPROVED);
        persist("A network packet is still a draft", "READING", QuestionStatus.DRAFT);

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
    void pageQuery_canNarrowToOneCefrLevelAndCountsOnlyThatLevel() {
        Question b2 = persist("First prompt", "READING", QuestionStatus.APPROVED);
        b2.setCefrLevel(CefrLevel.B2);
        questionRepository.saveAndFlush(b2);
        Question c1 = persist("Second prompt", "READING", QuestionStatus.APPROVED);
        c1.setCefrLevel(CefrLevel.C1);
        questionRepository.saveAndFlush(c1);
        persist("Unclassified prompt", "READING", QuestionStatus.APPROVED);

        Page<UUID> onlyB2 = questionRepository.findPagePublicIdsByCefr(
                null, null, null, null, "", null, CefrLevel.B2, PageRequest.of(0, 10));
        Page<UUID> any = questionRepository.findPagePublicIds(
                null, null, null, null, "", null, PageRequest.of(0, 10));

        assertThat(onlyB2.getContent()).containsExactly(b2.getPublicId());
        assertThat(onlyB2.getTotalElements()).isEqualTo(1);
        assertThat(any.getTotalElements()).isEqualTo(3);
    }

    @Test
    void groupedCounts_coverSharedQuestionBankOnly() {
        persist("prompt", "LISTENING", QuestionStatus.APPROVED);
        persist("prompt", "LISTENING", QuestionStatus.DRAFT);
        Question deletedQuestion = persist("prompt", "READING", QuestionStatus.APPROVED);
        deletedQuestion.setDeleted(true);
        questionRepository.saveAndFlush(deletedQuestion);

        assertThat(questionRepository.countByDeletedFalse()).isEqualTo(2);
        assertThat(questionRepository.countBySection())
                .containsExactlyInAnyOrder(
                        new Object[] { "LISTENING", 2L });
    }

    @Test
    void pageQuery_filtersByPool() {
        Question exam = persist("prompt", "READING", QuestionStatus.APPROVED);
        Question practice = persist("prompt", "READING", QuestionStatus.APPROVED,
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
    void pageQuery_poolFilterFollowsExplicitAvailabilityFlags() {
        Question movedToPractice = persist("prompt", "READING", QuestionStatus.DRAFT);
        movedToPractice.setAvailabilityFlagsExplicit(true);
        movedToPractice.setAvailableForExam(false);
        movedToPractice.setAvailableForPractice(true);
        questionRepository.saveAndFlush(movedToPractice);

        Page<UUID> practiceOnly = questionRepository.findPagePublicIds(
                null, null, null, QuestionPool.PRACTICE, "", null, PageRequest.of(0, 10));
        Page<UUID> examOnly = questionRepository.findPagePublicIds(
                null, null, null, QuestionPool.EXAM, "", null, PageRequest.of(0, 10));

        assertThat(practiceOnly.getContent()).containsExactly(movedToPractice.getPublicId());
        assertThat(examOnly.getContent()).isEmpty();
    }

    @Test
    void examGenerationQueries_ignorePracticeQuestions() {
        persist("prompt", "READING", QuestionStatus.APPROVED);
        persist("prompt", "READING", QuestionStatus.APPROVED, QuestionPool.PRACTICE);
        String key = PteTaskType.MC_READING_SINGLE.name();

        // Count projections only: H2 cannot convert native UUID result lists, Postgres can.
        assertThat(questionRepository.countPublishedSharedGroupedByTaskTypeKey(Set.of(key)))
                .singleElement()
                .satisfies(row -> assertThat(row.getCount()).isEqualTo(1L));
        assertThat(questionRepository.countPublishedSharedGroupedByTaskType(Set.of(key)))
                .singleElement()
                .satisfies(row -> assertThat(row.getCount()).isEqualTo(1L));
    }

    @Test
    void practiceQueryReturnsOnlyPublishedCurrentPracticeContentWithOptions() {
        Question practice = persist("Read this passage", "READING",
                QuestionStatus.APPROVED, QuestionPool.PRACTICE);
        QuestionOption option = new QuestionOption();
        option.setText("The supported statement");
        option.setCorrect(true);
        option.setOrderIndex(0);
        practice.addOption(option);
        questionRepository.saveAndFlush(practice);
        persist("Do not return this", "READING", QuestionStatus.APPROVED);

        List<UUID> ids = questionRepository.findPublishedPracticeQuestionIdsByTaskTypeKey(
                "MC_READING_SINGLE", PageRequest.of(0, 1));
        List<Question> result = questionRepository.findWithOptionsByPublicId(ids.get(0)).stream().toList();

        assertThat(result).singleElement().satisfies(question -> {
            assertThat(question.getPublicId()).isEqualTo(practice.getPublicId());
            assertThat(question.getOptions()).singleElement().extracting(QuestionOption::getText)
                    .isEqualTo("The supported statement");
        });
        assertThat(questionRepository.existsByDeletedFalseAndStatusAndCurrentTrueAndPoolAndTaskTypeKey(
                QuestionStatus.APPROVED, QuestionPool.PRACTICE, "MC_READING_SINGLE"))
                .isTrue();
    }

    @Test
    void optionsAreAChildTableReloadedInAuthoredOrderWithStableIds() {
        Question question = persist("prompt", "READING", QuestionStatus.APPROVED);
        QuestionOption second = new QuestionOption();
        second.setText("second");
        second.setOrderIndex(1);
        QuestionOption first = new QuestionOption();
        first.setText("first");
        first.setCorrect(true);
        first.setOrderIndex(0);
        question.addOption(second);
        question.addOption(first);
        questionRepository.saveAndFlush(question);
        UUID firstId = first.getPublicId();
        entityManager.clear();

        Question reloaded = questionRepository.findWithOptionsByPublicId(question.getPublicId()).orElseThrow();

        assertThat(reloaded.getOptions()).extracting(QuestionOption::getText).containsExactly("first", "second");
        assertThat(reloaded.getOptions().get(0).getPublicId()).isEqualTo(firstId);
        assertThat(reloaded.getOptions().get(0).isCorrect()).isTrue();
    }

    private Question persist(String prompt, String section, QuestionStatus status) {
        return persist(prompt, section, status, QuestionPool.EXAM);
    }

    private Question persist(String prompt, String section, QuestionStatus status,
            QuestionPool pool) {
        Question question = new Question();
        question.setPteTaskType(section.equals("READING") ? PteTaskType.MC_READING_SINGLE : PteTaskType.READ_ALOUD);
        question.setTaskTypeKey(question.getPteTaskType().name());
        question.setTaskTypeSection(section);
        question.setPool(pool);
        question.setStatus(status);
        question.setRevisionGroupPublicId(UUID.randomUUID());
        question.setRevisionNumber(1);
        question.setCurrent(true);
        question.setPromptText(prompt);
        return questionRepository.saveAndFlush(question);
    }
}
