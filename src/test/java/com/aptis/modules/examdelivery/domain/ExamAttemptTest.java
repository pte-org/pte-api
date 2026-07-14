package com.aptis.modules.examdelivery.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.aptis.common.exception.ApiException;

class ExamAttemptTest {

    private ExamAttempt newAttempt() {
        ExamAttempt attempt = new ExamAttempt();
        attempt.setStudentId("STU-001");
        attempt.setExamId(1L);
        return attempt;
    }

    @Test
    void submitSetsIsSubmittedAndSubmittedAt() {
        ExamAttempt attempt = newAttempt();

        assertFalse(attempt.getIsSubmitted());
        assertNull(attempt.getSubmittedAt());

        attempt.submit();

        assertTrue(attempt.getIsSubmitted());
        assertNotNull(attempt.getSubmittedAt());
    }

    @Test
    void submitTwiceThrows() {
        ExamAttempt attempt = newAttempt();
        attempt.submit();

        assertThrows(ApiException.class, attempt::submit);
    }

    @Test
    void submittedAtIsAfterOrEqualBeforeSubmit() {
        ExamAttempt attempt = newAttempt();
        LocalDateTime beforeSubmit = LocalDateTime.now();

        attempt.submit();

        assertTrue(attempt.getSubmittedAt().isAfter(beforeSubmit)
                || attempt.getSubmittedAt().isEqual(beforeSubmit));
    }

    @Test
    void getAnswersIsUnmodifiable() {
        ExamAttempt attempt = newAttempt();

        List<AttemptAnswer> answers = attempt.getAnswers();

        assertThrows(UnsupportedOperationException.class, () -> answers.add(new AttemptAnswer()));
    }

    @Test
    void getAnswersStartsEmpty() {
        ExamAttempt attempt = newAttempt();

        assertThat(attempt.getAnswers()).isEmpty();
    }
}
