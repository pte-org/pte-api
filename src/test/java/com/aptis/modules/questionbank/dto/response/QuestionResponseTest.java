package com.aptis.modules.questionbank.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.aptis.modules.questionbank.domain.Question;
import com.aptis.modules.questionbank.domain.enums.DifficultyLevel;
import com.aptis.modules.questionbank.domain.enums.PteTaskType;
import com.aptis.modules.questionbank.domain.enums.QuestionSource;
import com.aptis.modules.questionbank.domain.enums.QuestionStatus;
import com.aptis.modules.questionbank.domain.enums.QuestionType;
import com.aptis.modules.questionbank.domain.enums.Skill;

@DisplayName("QuestionResponse Tests")
class QuestionResponseTest {

    private Question question;

    @BeforeEach
    void setUp() {
        question = new Question();
        question.setPublicId(UUID.randomUUID());
        question.setVersion(1);
        question.setIsCurrent(true);
        question.setIsImmutable(false);
        question.setSkill(Skill.READING);
        question.setPart(1);
        question.setQuestionType(QuestionType.MULTIPLE_CHOICE);
        question.setContent("Test content");
        question.setInstruction("Test instruction");
        question.setScoreWeight(1.0f);
        question.setExplanation("Test explanation");
        question.setDifficultyLevel(DifficultyLevel.B1);
        question.setTopicTags(List.of("tag1"));
        question.setStatus(QuestionStatus.DRAFT);
        question.setCreatedBy(UUID.randomUUID());
        question.setTenantId(UUID.randomUUID());
        question.setSource(QuestionSource.HOST);
        question.setOptions(List.of("A", "B", "C"));
        question.setCorrectAnswers(List.of("A"));
    }

    @Test
    @DisplayName("from() includes pteTaskType from Question")
    void responseIncludesPteTaskType() {
        PteTaskType taskType = PteTaskType.READING_FILL_IN_THE_BLANKS;
        question.setPteTaskType(taskType);

        QuestionResponse response = QuestionResponse.from(question, List.of());

        assertThat(response.pteTaskType()).isEqualTo(taskType);
    }

    @Test
    @DisplayName("from() includes referenceAnswerText from Question")
    void responseIncludesReferenceAnswerText() {
        String referenceText = "Model answer for the question";
        question.setReferenceAnswerText(referenceText);

        QuestionResponse response = QuestionResponse.from(question, List.of());

        assertThat(response.referenceAnswerText()).isEqualTo(referenceText);
    }

    @Test
    @DisplayName("from() includes minWordCount from Question")
    void responseIncludesMinWordCount() {
        Integer minWords = 150;
        question.setMinWordCount(minWords);

        QuestionResponse response = QuestionResponse.from(question, List.of());

        assertThat(response.minWordCount()).isEqualTo(minWords);
    }

    @Test
    @DisplayName("from() includes maxWordCount from Question")
    void responseIncludesMaxWordCount() {
        Integer maxWords = 300;
        question.setMaxWordCount(maxWords);

        QuestionResponse response = QuestionResponse.from(question, List.of());

        assertThat(response.maxWordCount()).isEqualTo(maxWords);
    }

    @Test
    @DisplayName("from() includes all four new fields together")
    void responseIncludesAllNewFields() {
        PteTaskType taskType = PteTaskType.SUMMARIZE_WRITTEN_TEXT;
        String refText = "Expected summary should focus on main points";
        Integer minWords = 120;
        Integer maxWords = 250;

        question.setPteTaskType(taskType);
        question.setReferenceAnswerText(refText);
        question.setMinWordCount(minWords);
        question.setMaxWordCount(maxWords);

        QuestionResponse response = QuestionResponse.from(question, List.of());

        assertThat(response.pteTaskType()).isEqualTo(taskType);
        assertThat(response.referenceAnswerText()).isEqualTo(refText);
        assertThat(response.minWordCount()).isEqualTo(minWords);
        assertThat(response.maxWordCount()).isEqualTo(maxWords);
    }

    @Test
    @DisplayName("from() handles null pteTaskType")
    void responseHandlesNullPteTaskType() {
        question.setPteTaskType(null);

        QuestionResponse response = QuestionResponse.from(question, List.of());

        assertThat(response.pteTaskType()).isNull();
    }

    @Test
    @DisplayName("from() handles null referenceAnswerText")
    void responseHandlesNullReferenceAnswerText() {
        question.setReferenceAnswerText(null);

        QuestionResponse response = QuestionResponse.from(question, List.of());

        assertThat(response.referenceAnswerText()).isNull();
    }

    @Test
    @DisplayName("from() handles null word counts")
    void responseHandlesNullWordCounts() {
        question.setMinWordCount(null);
        question.setMaxWordCount(null);

        QuestionResponse response = QuestionResponse.from(question, List.of());

        assertThat(response.minWordCount()).isNull();
        assertThat(response.maxWordCount()).isNull();
    }

    @Test
    @DisplayName("from() with assets and audioAsset includes new fields")
    void responseWithAssetsIncludesNewFields() {
        PteTaskType taskType = PteTaskType.DESCRIBE_IMAGE;
        String refText = "Describe what you see";

        question.setPteTaskType(taskType);
        question.setReferenceAnswerText(refText);

        QuestionResponse response = QuestionResponse.from(question, List.of(), null);

        assertThat(response.pteTaskType()).isEqualTo(taskType);
        assertThat(response.referenceAnswerText()).isEqualTo(refText);
    }

    @Test
    @DisplayName("response preserves all existing fields plus new ones")
    void responsePreservesAllFields() {
        PteTaskType taskType = PteTaskType.MULTIPLE_CHOICE_LISTENING_SINGLE_ANSWER;
        question.setPteTaskType(taskType);
        question.setMinWordCount(100);

        QuestionResponse response = QuestionResponse.from(question, List.of());

        // Existing fields
        assertThat(response.skill()).isEqualTo(Skill.READING);
        assertThat(response.part()).isEqualTo(1);
        assertThat(response.questionType()).isEqualTo(QuestionType.MULTIPLE_CHOICE);
        assertThat(response.content()).isEqualTo("Test content");
        // New fields
        assertThat(response.pteTaskType()).isEqualTo(taskType);
        assertThat(response.minWordCount()).isEqualTo(100);
    }

}
