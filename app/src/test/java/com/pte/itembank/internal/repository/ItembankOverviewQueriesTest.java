package com.pte.itembank.internal.repository;

import com.pte.itembank.ItembankOverviewService;
import com.pte.itembank.ItembankOverviewService.QuestionBankCounts;
import com.pte.itembank.ItembankService;
import com.pte.itembank.domain.Question;
import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.itembank.domain.enums.QuestionPool;
import com.pte.itembank.domain.enums.QuestionStatus;
import com.pte.itembank.domain.enums.Visibility;
import com.pte.itembank.dto.response.QuestionStatsResponse;
import com.pte.itembank.internal.controller.QuestionController;
import com.pte.itembank.internal.service.ItembankAccessPolicy;
import com.pte.itembank.internal.service.QuestionValidationHelper;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.web.ApiResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Question bank figures behind {@link ItembankOverviewService} on the embedded database: counts by status, skill
 * section and pool with zero-filled enum keys, approved-by-pool, created-in-window on half-open ranges and the exact
 * same row population as the existing {@link ItembankService#stats} (which must stay unchanged).
 */
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class ItembankOverviewQueriesTest {

    private static final Instant FROM = Instant.parse("2026-09-13T03:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-13T03:00:00Z");

    @Autowired
    private QuestionRepository questions;
    @Autowired
    private EntityManager em;
    @Autowired
    private EntityManagerFactory emf;

    private ItembankOverviewService service;
    private ItembankService legacy;
    private int counter;

    @BeforeEach
    void setUp() {
        service = new ItembankOverviewService(questions);
        legacy = com.pte.itembank.ItembankFixtures.itembankService(questions, new QuestionValidationHelper(null), new ItembankAccessPolicy());
    }

    // ---- builders ----

    private Question question(String section, QuestionStatus status, QuestionPool pool, boolean deleted) {
        Question question = new Question();
        PteTaskType type = switch (section) {
            case "READING" -> PteTaskType.MC_READING_SINGLE;
            default -> PteTaskType.READ_ALOUD;
        };
        question.setPteTaskType(type);
        question.setTaskTypeKey(type.name() + (++counter));
        question.setTaskTypeSection(section);
        question.setVisibility(Visibility.SHARED);
        question.setPool(pool);
        question.setStatus(status);
        question.setRevisionGroupPublicId(UUID.randomUUID());
        question.setRevisionNumber(1);
        question.setCurrent(true);
        question.setTitle("Secret title " + counter);
        question.setPromptText("Secret prompt " + counter);
        question.setDeleted(deleted);
        return questions.saveAndFlush(question);
    }

    private Question question(String section, QuestionStatus status, QuestionPool pool) {
        return question(section, status, pool, false);
    }

    private Question createdAt(Question question, Instant createdAt) {
        em.createQuery("update Question q set q.createdAt = :c where q.id = :id").setParameter("c", createdAt)
                .setParameter("id", question.getId()).executeUpdate();
        em.clear();
        return question;
    }

    private static CurrentUser caller() {
        return new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"));
    }

    // ---- guards on the enums the zero-filling relies on ----

    @Test
    void theEnumsTheCountsAreBuiltFromAreTheExpectedOnes() {
        assertThat(QuestionStatus.values()).extracting(Enum::name)
                .containsExactly("DRAFT", "PENDING_APPROVAL", "APPROVED", "ARCHIVED");
        assertThat(QuestionPool.values()).extracting(Enum::name).containsExactly("EXAM", "PRACTICE");
        // The bank has a single visibility: "non-shared are excluded" is vacuous until a second one is added,
        // at which point this guard forces a decision about the overview.
        assertThat(Visibility.values()).containsExactly(Visibility.SHARED);
    }

    // ---- zero-filled keys ----

    @Test
    void anEmptyBankHasEveryStatusSectionAndPoolKeyWithZero() {
        QuestionBankCounts counts = service.counts();

        assertThat(counts.total()).isZero();
        assertThat(counts.byStatus()).containsExactly(Map.entry("DRAFT", 0L), Map.entry("PENDING_APPROVAL", 0L),
                Map.entry("APPROVED", 0L), Map.entry("ARCHIVED", 0L));
        assertThat(counts.bySection()).containsExactly(Map.entry("LISTENING", 0L), Map.entry("SPEAKING", 0L),
                Map.entry("READING", 0L), Map.entry("WRITING", 0L));
        assertThat(counts.byPool()).containsExactly(Map.entry("EXAM", 0L), Map.entry("PRACTICE", 0L));
        assertThat(counts.approvedByPool()).containsExactly(Map.entry("EXAM", 0L), Map.entry("PRACTICE", 0L));
    }

    @Test
    void keysNeverUsedByAnyRowStayAtZeroWhileTheUsedOnesAreCounted() {
        question("READING", QuestionStatus.DRAFT, QuestionPool.EXAM);

        QuestionBankCounts counts = service.counts();

        assertThat(counts.total()).isEqualTo(1);
        assertThat(counts.byStatus()).containsEntry("DRAFT", 1L).containsEntry("APPROVED", 0L)
                .containsEntry("PENDING_APPROVAL", 0L).containsEntry("ARCHIVED", 0L);
        assertThat(counts.bySection()).containsEntry("READING", 1L).containsEntry("LISTENING", 0L)
                .containsEntry("SPEAKING", 0L).containsEntry("WRITING", 0L);
        assertThat(counts.byPool()).containsEntry("EXAM", 1L).containsEntry("PRACTICE", 0L);
        assertThat(counts.approvedByPool()).containsEntry("EXAM", 0L).containsEntry("PRACTICE", 0L);
    }

    // ---- counting ----

    @Test
    void countsBreakDownByStatusSectionAndPoolAndAddUpToTheTotal() {
        question("READING", QuestionStatus.APPROVED, QuestionPool.EXAM);
        question("READING", QuestionStatus.APPROVED, QuestionPool.EXAM);
        question("READING", QuestionStatus.APPROVED, QuestionPool.PRACTICE);
        question("LISTENING", QuestionStatus.DRAFT, QuestionPool.EXAM);
        question("SPEAKING", QuestionStatus.PENDING_APPROVAL, QuestionPool.PRACTICE);
        question("WRITING", QuestionStatus.ARCHIVED, QuestionPool.EXAM);
        question("WRITING", QuestionStatus.DRAFT, QuestionPool.PRACTICE);

        QuestionBankCounts counts = service.counts();

        assertThat(counts.total()).isEqualTo(7);
        assertThat(counts.byStatus()).containsEntry("APPROVED", 3L).containsEntry("DRAFT", 2L)
                .containsEntry("PENDING_APPROVAL", 1L).containsEntry("ARCHIVED", 1L);
        assertThat(counts.bySection()).containsEntry("READING", 3L).containsEntry("LISTENING", 1L)
                .containsEntry("SPEAKING", 1L).containsEntry("WRITING", 2L);
        assertThat(counts.byPool()).containsEntry("EXAM", 4L).containsEntry("PRACTICE", 3L);
        assertThat(counts.byStatus().values().stream().mapToLong(Long::longValue).sum()).isEqualTo(counts.total());
        assertThat(counts.bySection().values().stream().mapToLong(Long::longValue).sum()).isEqualTo(counts.total());
        assertThat(counts.byPool().values().stream().mapToLong(Long::longValue).sum()).isEqualTo(counts.total());
    }

    @Test
    void approvedByPoolCountsOnlyApprovedQuestionsSoPracticeItemsAreNeverMistakenForExamStock() {
        question("READING", QuestionStatus.APPROVED, QuestionPool.EXAM);
        question("READING", QuestionStatus.APPROVED, QuestionPool.PRACTICE);
        question("READING", QuestionStatus.APPROVED, QuestionPool.PRACTICE);
        question("READING", QuestionStatus.DRAFT, QuestionPool.EXAM);
        question("READING", QuestionStatus.PENDING_APPROVAL, QuestionPool.EXAM);
        question("READING", QuestionStatus.ARCHIVED, QuestionPool.PRACTICE);

        QuestionBankCounts counts = service.counts();

        assertThat(counts.approvedByPool()).containsEntry("EXAM", 1L).containsEntry("PRACTICE", 2L);
        assertThat(counts.byPool()).containsEntry("EXAM", 3L).containsEntry("PRACTICE", 3L);
    }

    @Test
    void softDeletedQuestionsAreNeverCounted() {
        question("READING", QuestionStatus.APPROVED, QuestionPool.EXAM);
        question("READING", QuestionStatus.APPROVED, QuestionPool.EXAM, true);
        question("LISTENING", QuestionStatus.DRAFT, QuestionPool.PRACTICE, true);

        QuestionBankCounts counts = service.counts();

        assertThat(counts.total()).isEqualTo(1);
        assertThat(counts.byStatus()).containsEntry("APPROVED", 1L).containsEntry("DRAFT", 0L);
        assertThat(counts.bySection()).containsEntry("READING", 1L).containsEntry("LISTENING", 0L);
        assertThat(counts.byPool()).containsEntry("EXAM", 1L).containsEntry("PRACTICE", 0L);
    }

    @Test
    void everyRevisionIsCountedSoASupersededRevisionShowsAsArchived() {
        Question original = question("READING", QuestionStatus.APPROVED, QuestionPool.EXAM);
        Question revision = question("READING", QuestionStatus.DRAFT, QuestionPool.EXAM);
        revision.setRevisionGroupPublicId(original.getRevisionGroupPublicId());
        revision.setRevisionNumber(2);
        revision.setSupersedesPublicId(original.getPublicId());
        questions.saveAndFlush(revision);
        original.setStatus(QuestionStatus.ARCHIVED);
        original.setCurrent(false);
        questions.saveAndFlush(original);

        QuestionBankCounts counts = service.counts();

        assertThat(counts.total()).isEqualTo(2);
        assertThat(counts.byStatus()).containsEntry("ARCHIVED", 1L).containsEntry("DRAFT", 1L);
    }

    @Test
    void aTenantStampedLegacyRowIsCountedExactlyLikeTheExistingStatsDoes() {
        Question row = question("WRITING", QuestionStatus.APPROVED, QuestionPool.EXAM);
        row.setTenantId(UUID.randomUUID());
        questions.saveAndFlush(row);

        assertThat(service.counts().total()).isEqualTo(1);
        assertThat(legacy.stats(caller()).total()).isEqualTo(1);
    }

    @Test
    void aSectionNameOutsideTheFourKnownOnesIsKeptNotDropped() {
        question("READING", QuestionStatus.APPROVED, QuestionPool.EXAM);
        question("CUSTOM", QuestionStatus.APPROVED, QuestionPool.EXAM);

        QuestionBankCounts counts = service.counts();

        assertThat(counts.total()).isEqualTo(2);
        assertThat(counts.bySection()).containsEntry("CUSTOM", 1L).containsEntry("READING", 1L)
                .containsKeys("LISTENING", "SPEAKING", "WRITING");
    }

    // ---- equality with the existing stats ----

    @Test
    void theOverviewCountsAgreeWithTheExistingStatsOnTheSameRows() {
        question("LISTENING", QuestionStatus.APPROVED, QuestionPool.EXAM);
        question("LISTENING", QuestionStatus.DRAFT, QuestionPool.PRACTICE);
        question("SPEAKING", QuestionStatus.DRAFT, QuestionPool.EXAM);
        question("READING", QuestionStatus.PENDING_APPROVAL, QuestionPool.EXAM);
        question("READING", QuestionStatus.DRAFT, QuestionPool.EXAM);
        question("WRITING", QuestionStatus.ARCHIVED, QuestionPool.PRACTICE);
        question("WRITING", QuestionStatus.DRAFT, QuestionPool.EXAM, true); // deleted: in neither
        question("SPEAKING", QuestionStatus.APPROVED, QuestionPool.EXAM, true); // deleted: in neither

        QuestionStatsResponse stats = legacy.stats(caller());
        QuestionBankCounts counts = service.counts();

        assertThat(counts.total()).isEqualTo(stats.total()).isEqualTo(6);
        assertThat(counts.bySection().get("LISTENING")).isEqualTo(stats.listening());
        assertThat(counts.bySection().get("READING")).isEqualTo(stats.reading());
        assertThat(counts.bySection().get("WRITING")).isEqualTo(stats.writing());
        assertThat(counts.bySection().get("SPEAKING")).isEqualTo(stats.speaking());
        assertThat(counts.byStatus().get("DRAFT")).isEqualTo(stats.draft()).isEqualTo(3);
    }

    @Test
    void theExistingStatsKeepTheirContractRecordShapeEndpointAndRepositoryQueries() throws Exception {
        // response shape
        assertThat(Arrays.stream(QuestionStatsResponse.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("total", "listening", "reading", "writing", "speaking", "draft");
        // endpoint: GET /api/v1/questions/stats -> ApiResponse<QuestionStatsResponse>
        Method handler = QuestionController.class.getMethod("stats");
        assertThat(handler.getAnnotation(GetMapping.class).value()).containsExactly("/stats");
        assertThat(handler.getReturnType()).isEqualTo(ApiResponse.class);
        assertThat(((ParameterizedType) handler.getGenericReturnType()).getActualTypeArguments())
                .containsExactly(QuestionStatsResponse.class);
        assertThat(handler.getParameterCount()).isZero();
        // service entry point and the two legacy grouped queries are still there with their old signatures
        assertThat(ItembankService.class.getMethod("stats", CurrentUser.class).getReturnType())
                .isEqualTo(QuestionStatsResponse.class);
        assertThat(QuestionRepository.class.getMethod("countBySectionAndVisibility", Visibility.class)).isNotNull();
        assertThat(QuestionRepository.class.getMethod("countByStatusAndVisibility", Visibility.class)).isNotNull();
        assertThat(QuestionRepository.class.getMethod("countByDeletedFalseAndVisibility", Visibility.class)).isNotNull();
    }

    @Test
    void theExistingStatsStillReportTheirSectionAndDraftCountsFromTheDatabase() {
        question("LISTENING", QuestionStatus.APPROVED, QuestionPool.EXAM);
        question("READING", QuestionStatus.DRAFT, QuestionPool.EXAM);
        question("READING", QuestionStatus.DRAFT, QuestionPool.PRACTICE);

        QuestionStatsResponse stats = legacy.stats(caller());

        assertThat(stats).isEqualTo(new QuestionStatsResponse(3, 1, 2, 0, 0, 2));
    }

    // ---- created in window ----

    @Test
    void createdInWindowIsHalfOpenIncludingTheLowerBoundAndExcludingTheUpperBound() {
        createdAt(question("READING", QuestionStatus.DRAFT, QuestionPool.EXAM), FROM); // included
        createdAt(question("READING", QuestionStatus.APPROVED, QuestionPool.EXAM), FROM.plusSeconds(1)); // included
        createdAt(question("READING", QuestionStatus.ARCHIVED, QuestionPool.EXAM), TO.minusNanos(1_000)); // included
        createdAt(question("READING", QuestionStatus.DRAFT, QuestionPool.EXAM), TO); // excluded
        createdAt(question("READING", QuestionStatus.DRAFT, QuestionPool.EXAM), FROM.minusNanos(1_000)); // excluded

        assertThat(service.countCreatedBetween(FROM, TO)).isEqualTo(3);
    }

    @Test
    void adjacentWindowsPartitionTheQuestionsAndTheStatusAtCreationIsIrrelevant() {
        Instant previousFrom = FROM.minusSeconds(30L * 86_400);
        createdAt(question("READING", QuestionStatus.DRAFT, QuestionPool.EXAM), previousFrom);
        createdAt(question("READING", QuestionStatus.APPROVED, QuestionPool.PRACTICE), FROM.minusNanos(1_000));
        createdAt(question("READING", QuestionStatus.ARCHIVED, QuestionPool.EXAM), FROM);
        createdAt(question("LISTENING", QuestionStatus.PENDING_APPROVAL, QuestionPool.EXAM), TO.minusNanos(1_000));

        long previous = service.countCreatedBetween(previousFrom, FROM);
        long current = service.countCreatedBetween(FROM, TO);

        assertThat(previous).isEqualTo(2);
        assertThat(current).isEqualTo(2);
        assertThat(previous + current).isEqualTo(service.countCreatedBetween(previousFrom, TO));
    }

    @Test
    void softDeletedQuestionsAreNotInTheCreatedCountAndAnEmptyRangeIsZero() {
        createdAt(question("READING", QuestionStatus.DRAFT, QuestionPool.EXAM, true), FROM.plusSeconds(10));
        createdAt(question("READING", QuestionStatus.DRAFT, QuestionPool.EXAM), FROM.plusSeconds(20));

        assertThat(service.countCreatedBetween(FROM, TO)).isEqualTo(1);
        assertThat(service.countCreatedBetween(FROM, FROM)).isZero();
        assertThat(service.countCreatedBetween(TO, FROM)).isZero();
        assertThat(service.countCreatedBetween(TO, TO.plusSeconds(86_400))).isZero();
    }

    @Test
    void anEmptyBankHasZeroCreatedInAnyWindow() {
        assertThat(service.countCreatedBetween(FROM, TO)).isZero();
    }

    // ---- statements ----

    @Test
    void theCountsAreOneGroupedStatementWithNoEntityLoadedAndNoQuestionTextRead() {
        for (int i = 0; i < 12; i++) {
            question(i % 2 == 0 ? "READING" : "LISTENING", QuestionStatus.values()[i % 4],
                    i % 3 == 0 ? QuestionPool.PRACTICE : QuestionPool.EXAM);
        }
        em.clear();
        Statistics statistics = emf.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        QuestionBankCounts counts = service.counts();

        assertThat(counts.total()).isEqualTo(12);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        assertThat(statistics.getEntityLoadCount()).isZero();
    }

    @Test
    void theCreatedCountIsOneStatementWithNoEntityLoaded() {
        createdAt(question("READING", QuestionStatus.DRAFT, QuestionPool.EXAM), FROM.plusSeconds(1));
        Statistics statistics = emf.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        service.countCreatedBetween(FROM, TO);

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        assertThat(statistics.getEntityLoadCount()).isZero();
    }

    @Test
    void theFacadeRowCarriesCountsOnlyAndTheMapsAreUnmodifiable() {
        question("READING", QuestionStatus.APPROVED, QuestionPool.EXAM);

        QuestionBankCounts counts = service.counts();

        assertThat(Arrays.stream(QuestionBankCounts.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("total", "byStatus", "bySection", "byPool", "approvedByPool");
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> counts.byStatus().put("X", 1L));
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> counts.byPool().clear());
        assertThat(counts.toString()).doesNotContain("Secret title").doesNotContain("Secret prompt");
    }
}
