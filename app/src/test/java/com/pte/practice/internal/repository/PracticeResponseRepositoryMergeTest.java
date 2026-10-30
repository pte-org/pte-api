package com.pte.practice.internal.repository;

import com.pte.practice.internal.domain.PracticeResponse;
import com.pte.practice.internal.domain.PracticeSession;
import com.pte.practice.internal.domain.enums.PracticeResponseStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:practice-response-merge;DB_CLOSE_DELAY=-1"
})
@ContextConfiguration(classes = PracticeResponseRepositoryMergeTest.PracticeJpaConfiguration.class)
class PracticeResponseRepositoryMergeTest {

    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = PracticeSession.class)
    @EnableJpaRepositories(basePackageClasses = PracticeResponseRepository.class)
    static class PracticeJpaConfiguration {
    }

    @Autowired
    private PracticeSessionRepository sessions;
    @Autowired
    private PracticeResponseRepository responses;

    @Test
    void renamedResponseQueriesPreserveStudentIsolationDistinctQuestionsAndScoreAverages() {
        UUID student = UUID.randomUUID();
        PracticeSession first = session(student);
        PracticeSession second = session(student);
        PracticeSession otherStudent = session(UUID.randomUUID());
        UUID repeated = UUID.randomUUID();
        UUID skippedQuestion = UUID.randomUUID();
        UUID foreignQuestion = UUID.randomUUID();
        PracticeResponse objective = response(first, 0, repeated, PracticeResponseStatus.ANSWERED, 20, null);
        PracticeResponse skipped = response(first, 1, skippedQuestion, PracticeResponseStatus.SKIPPED, null, null);
        PracticeResponse deleted = response(first, 2, UUID.randomUUID(), PracticeResponseStatus.ANSWERED, 100, null);
        deleted.setDeleted(true);
        PracticeResponse ai = response(second, 0, repeated, PracticeResponseStatus.ANSWERED, 10, 80);
        PracticeResponse foreign = response(otherStudent, 0, foreignQuestion,
                PracticeResponseStatus.ANSWERED, 100, null);
        responses.saveAllAndFlush(List.of(objective, skipped, deleted, ai, foreign));

        assertThat(responses.findSeenSourceQuestionIds(student, List.of("READ_ALOUD")))
                .containsExactlyInAnyOrder(repeated, skippedQuestion);
        assertThat(responses.findSeenSourceQuestionIds(otherStudent.getStudentPublicId(), List.of("READ_ALOUD")))
                .containsExactly(foreignQuestion);
        assertThat(responses.findSeenSourceQuestionIds(student, List.of("WRITE_ESSAY"))).isEmpty();
        assertThat(responses.countAnsweredSourceQuestionsByTaskCode(student)).singleElement().satisfies(row -> {
            assertThat(row[0]).isEqualTo("READ_ALOUD");
            assertThat(((Number) row[1]).longValue()).isEqualTo(1);
        });
        assertThat(responses.averageScoreByTaskCode(student)).singleElement().satisfies(row -> {
            assertThat(row[0]).isEqualTo("READ_ALOUD");
            assertThat(((Number) row[1]).doubleValue()).isEqualTo(50.0);
        });
        assertThat(responses.findByPracticeSessionIdInAndDeletedFalse(List.of(first.getId(), second.getId())))
                .extracting(PracticeResponse::getPublicId)
                .containsExactlyInAnyOrder(objective.getPublicId(), skipped.getPublicId(), ai.getPublicId());
        assertThat(responses.findByPracticeSessionIdInAndDeletedFalseOrderByPracticeSessionIdAscOrderIndexAsc(
                List.of(second.getId(), first.getId())))
                .extracting(PracticeResponse::getPublicId)
                .containsExactly(objective.getPublicId(), skipped.getPublicId(), ai.getPublicId());
    }

    private PracticeSession session(UUID student) {
        PracticeSession session = new PracticeSession();
        session.setStudentPublicId(student);
        session.setTenantId(UUID.randomUUID());
        session.setProductCode("PTE_ACADEMIC");
        session.setTitle("Practice regression");
        session.setCatalogVersion("test");
        session.setStartIdempotencyKey(UUID.randomUUID().toString());
        session.setStartRequestHash("test");
        return sessions.saveAndFlush(session);
    }

    private PracticeResponse response(PracticeSession session, int order, UUID question,
            PracticeResponseStatus status, Integer objectiveScore, Integer aiScore) {
        PracticeResponse response = new PracticeResponse();
        response.setPracticeSessionId(session.getId());
        response.setOrderIndex(order);
        response.setTaskCode("READ_ALOUD");
        response.setDisplayName("Read Aloud");
        response.setSection("SPEAKING");
        response.setRendererKey("READ_ALOUD_V1");
        response.setSourceQuestionPublicId(question);
        response.setStatus(status);
        response.setObjectiveScore(objectiveScore);
        response.setAiScore(aiScore);
        return response;
    }
}
