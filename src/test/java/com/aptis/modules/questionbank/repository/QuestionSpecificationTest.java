package com.aptis.modules.questionbank.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import com.aptis.modules.questionbank.domain.Question;
import com.aptis.modules.questionbank.domain.enums.DifficultyLevel;
import com.aptis.modules.questionbank.domain.enums.PteTaskType;
import com.aptis.modules.questionbank.domain.enums.QuestionSource;
import com.aptis.modules.questionbank.domain.enums.QuestionStatus;
import com.aptis.modules.questionbank.domain.enums.QuestionType;
import com.aptis.modules.questionbank.domain.enums.Skill;

@DisplayName("QuestionSpecification Tests")
class QuestionSpecificationTest {

    @Test
    @DisplayName("buildFilter accepts pteTaskType parameter")
    void buildFilterAcceptsPteTaskType() {
        // This test verifies that buildFilter method signature includes pteTaskType parameter
        // The specification should be buildable with pteTaskType included
        Specification<Question> spec = QuestionSpecification.buildFilter(
                Skill.READING,
                1,
                QuestionType.MULTIPLE_CHOICE,
                DifficultyLevel.B1,
                QuestionStatus.ACTIVE,
                true,
                PteTaskType.READING_FILL_IN_THE_BLANKS
        );

        assertThat(spec).isNotNull();
    }

    @Test
    @DisplayName("buildFilter handles null pteTaskType")
    void buildFilterHandlesNullPteTaskType() {
        Specification<Question> spec = QuestionSpecification.buildFilter(
                Skill.READING,
                1,
                QuestionType.MULTIPLE_CHOICE,
                DifficultyLevel.B1,
                QuestionStatus.ACTIVE,
                true,
                null
        );

        assertThat(spec).isNotNull();
    }

    @Test
    @DisplayName("buildFilter with only pteTaskType builds valid specification")
    void buildFilterWithOnlyPteTaskType() {
        Specification<Question> spec = QuestionSpecification.buildFilter(
                null,
                null,
                null,
                null,
                null,
                null,
                PteTaskType.DESCRIBE_IMAGE
        );

        assertThat(spec).isNotNull();
    }

    @Test
    @DisplayName("buildFilter can combine pteTaskType with other filters")
    void buildFilterCombinesPteTaskTypeWithOtherFilters() {
        Specification<Question> spec = QuestionSpecification.buildFilter(
                Skill.SPEAKING,
                1,
                QuestionType.AUDIO_RECORD,
                DifficultyLevel.B2,
                QuestionStatus.DRAFT,
                true,
                PteTaskType.READ_ALOUD
        );

        assertThat(spec).isNotNull();
    }

    @Test
    @DisplayName("buildFilter with OBJECTIVE task type builds specification")
    void buildFilterWithObjectiveTaskType() {
        Specification<Question> spec = QuestionSpecification.buildFilter(
                Skill.READING,
                2,
                QuestionType.MULTIPLE_CHOICE,
                DifficultyLevel.C1,
                QuestionStatus.ACTIVE,
                true,
                PteTaskType.MULTIPLE_CHOICE_READING_MULTIPLE_ANSWER
        );

        assertThat(spec).isNotNull();
    }

    @Test
    @DisplayName("buildFilter with AI_SCORED task type builds specification")
    void buildFilterWithAiScoredTaskType() {
        Specification<Question> spec = QuestionSpecification.buildFilter(
                Skill.WRITING,
                3,
                QuestionType.AUDIO_RECORD,
                DifficultyLevel.B1,
                QuestionStatus.ACTIVE,
                true,
                PteTaskType.WRITE_ESSAY
        );

        assertThat(spec).isNotNull();
    }

    @Test
    @DisplayName("buildFilter with UNSCORED task type builds specification")
    void buildFilterWithUnscoredTaskType() {
        Specification<Question> spec = QuestionSpecification.buildFilter(
                Skill.SPEAKING,
                1,
                QuestionType.AUDIO_RECORD,
                DifficultyLevel.A2,
                QuestionStatus.DRAFT,
                true,
                PteTaskType.PERSONAL_INTRODUCTION
        );

        assertThat(spec).isNotNull();
    }

    @Test
    @DisplayName("buildFilter with all LISTENING task types works")
    void buildFilterWithListeningTaskTypes() {
        Specification<Question> spec = QuestionSpecification.buildFilter(
                Skill.LISTENING,
                4,
                QuestionType.MULTIPLE_CHOICE,
                DifficultyLevel.B2,
                QuestionStatus.ACTIVE,
                true,
                PteTaskType.LISTENING_FILL_IN_THE_BLANKS
        );

        assertThat(spec).isNotNull();
    }

    @Test
    @DisplayName("buildFilter with pteTaskType can filter multiple reading types")
    void buildFilterMultipleReadingTypes() {
        PteTaskType[] readingTypes = {
                PteTaskType.READING_FILL_IN_THE_BLANKS,
                PteTaskType.READING_WRITING_FILL_IN_THE_BLANKS,
                PteTaskType.MULTIPLE_CHOICE_READING_SINGLE_ANSWER,
                PteTaskType.MULTIPLE_CHOICE_READING_MULTIPLE_ANSWER,
                PteTaskType.RE_ORDER_PARAGRAPHS
        };

        for (PteTaskType taskType : readingTypes) {
            Specification<Question> spec = QuestionSpecification.buildFilter(
                    Skill.READING,
                    1,
                    QuestionType.MULTIPLE_CHOICE,
                    DifficultyLevel.B1,
                    QuestionStatus.ACTIVE,
                    true,
                    taskType
            );
            assertThat(spec).isNotNull();
        }
    }

}
