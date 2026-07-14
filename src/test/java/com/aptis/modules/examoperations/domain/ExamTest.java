package com.aptis.modules.examoperations.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class ExamTest {

    private Exam newExam() {
        Exam exam = new Exam();
        exam.setName("Math Exam");
        exam.setCode("MATH-001");
        exam.setHostId("HOST-1");
        return exam;
    }

    private ExamQuestion newQuestion(Long questionId, boolean usedInAssignedBatch) {
        ExamQuestion question = new ExamQuestion();
        question.setQuestionId(questionId);
        question.setSequence(1);
        question.setIsUsedInAssignedBatch(usedInAssignedBatch);
        return question;
    }

    @Test
    void addQuestionSetsIsAssignableTrue() {
        Exam exam = newExam();

        exam.addQuestion(newQuestion(1L, false));

        assertThat(exam.getQuestions()).hasSize(1);
        assertTrue(exam.getIsAssignable());
    }

    @Test
    void addNullQuestionThrows() {
        Exam exam = newExam();

        assertThrows(ExamConstraintViolationException.class, () -> exam.addQuestion(null));
    }

    @Test
    void addDuplicateQuestionThrows() {
        Exam exam = newExam();
        exam.addQuestion(newQuestion(1L, false));

        assertThrows(ExamConstraintViolationException.class, () -> exam.addQuestion(newQuestion(1L, false)));
    }

    @Test
    void removeQuestionSuccessSetsIsAssignableFalseWhenEmpty() {
        Exam exam = newExam();
        exam.addQuestion(newQuestion(1L, false));

        exam.removeQuestion(1L);

        assertThat(exam.getQuestions()).isEmpty();
        assertFalse(exam.getIsAssignable());
    }

    @Test
    void removeQuestionInAssignedBatchThrows() {
        Exam exam = newExam();
        exam.addQuestion(newQuestion(1L, true));

        assertThrows(ExamConstraintViolationException.class, () -> exam.removeQuestion(1L));
    }

    @Test
    void removeQuestionNotFoundThrows() {
        Exam exam = newExam();

        assertThrows(ExamConstraintViolationException.class, () -> exam.removeQuestion(99L));
    }

    @Test
    void getQuestionsIsUnmodifiable() {
        Exam exam = newExam();
        exam.addQuestion(newQuestion(1L, false));

        List<ExamQuestion> questions = exam.getQuestions();

        assertThrows(UnsupportedOperationException.class, () -> questions.add(newQuestion(2L, false)));
    }
}
