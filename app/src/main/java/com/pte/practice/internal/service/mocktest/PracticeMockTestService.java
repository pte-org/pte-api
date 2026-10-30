package com.pte.practice.internal.service.mocktest;

import com.pte.practice.internal.service.selection.PracticeQuestionPicker;
import com.pte.practice.internal.service.timing.PracticeTimingService;

import com.pte.itembank.PracticeQuestionService;
import static com.pte.practice.internal.service.mocktest.PracticeMockTestAssembler.toResponse;
import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.practice.internal.domain.PracticeMockTest;
import com.pte.practice.internal.domain.PracticeMockTestItem;
import com.pte.practice.internal.domain.PracticeSession;
import com.pte.practice.internal.domain.PracticeResponse;
import com.pte.practice.internal.domain.enums.PracticeMockTestStatus;
import com.pte.practice.internal.domain.enums.PracticeResponseStatus;
import com.pte.practice.internal.domain.enums.PracticeSessionStatus;
import com.pte.practice.internal.dto.response.mocktest.PracticeMockTestResponse;
import com.pte.practice.internal.exception.PracticeSessionException;
import com.pte.practice.internal.repository.PracticeMockTestItemRepository;
import com.pte.practice.internal.repository.PracticeMockTestRepository;
import com.pte.practice.internal.repository.PracticeResponseRepository;
import com.pte.practice.internal.repository.PracticeSessionRepository;
import com.pte.assessment.ExamDefinitionService;
import com.pte.assessment.dto.response.ExamDefinitionItemResponse;
import com.pte.assessment.dto.response.ExamDefinitionResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Builds and lists fixed full mock tests. A test is generated once from the active Score Template (the template's
 * {@code minCount} questions per task type) and the practice question pool; the chosen questions are stored with the
 * test, so every student gets the same test and it stays the same when the question bank changes.
 */
@Service
public class PracticeMockTestService {

    private static final List<String> SECTION_ORDER = List.of("SPEAKING", "WRITING", "READING", "LISTENING");

    private final PracticeMockTestRepository mockTestRepository;
    private final PracticeMockTestItemRepository mockTestItemRepository;
    private final PracticeSessionRepository sessionRepository;
    private final PracticeResponseRepository sessionItemRepository;
    private final PracticeQuestionService questionService;
    private final ExamDefinitionService scoreTemplateService;
    private final PracticeTimingService timingService;
    private final PracticeScoreEstimator estimator;
    private final PracticeMockTestAssembler assembler;

    @PersistenceContext
    private EntityManager entityManager;

    public PracticeMockTestService(PracticeMockTestRepository mockTestRepository,
            PracticeMockTestItemRepository mockTestItemRepository, PracticeSessionRepository sessionRepository,
            PracticeResponseRepository sessionItemRepository, PracticeQuestionService questionService,
            PracticeQuestionPicker questionPicker, ExamDefinitionService scoreTemplateService,
            PracticeTimingService timingService, PracticeScoreEstimator estimator) {
        this.mockTestRepository = mockTestRepository;
        this.mockTestItemRepository = mockTestItemRepository;
        this.sessionRepository = sessionRepository;
        this.sessionItemRepository = sessionItemRepository;
        this.questionService = questionService;
        this.scoreTemplateService = scoreTemplateService;
        this.timingService = timingService;
        this.estimator = estimator;
        this.assembler = new PracticeMockTestAssembler(mockTestRepository, mockTestItemRepository, questionPicker);
    }

    /**
     * Builds {@code count} new tests, numbered after the existing ones. All or nothing: when the bank has too few
     * questions for any task type, nothing is created and the error names the types that are short.
     */
    @Transactional
    public List<PracticeMockTestResponse> generate(int count) {
        ExamDefinitionResponse template = scoreTemplateService.findActive().orElseThrow(() -> new PracticeSessionException(
                HttpStatus.UNPROCESSABLE_ENTITY, PracticeConstants.PRACTICE_MOCK_TEST_TEMPLATE_MISSING,
                PracticeConstants.PRACTICE_MOCK_TEST_TEMPLATE_MISSING_MESSAGE));
        List<ExamDefinitionItemResponse> plan = orderedTemplateItems(template);
        Map<String, List<UUID>> candidates = new LinkedHashMap<>();
        List<String> shortTypes = new ArrayList<>();
        for (ExamDefinitionItemResponse item : plan) {
            String code = typeCode(item);
            List<UUID> pool = questionService.findPublishedCandidateIds(code);
            candidates.put(code, pool);
            if (pool.size() < item.minCount()) {
                shortTypes.add(code + " (" + pool.size() + "/" + item.minCount() + ")");
            }
        }
        if (!shortTypes.isEmpty()) {
            throw new PracticeSessionException(HttpStatus.UNPROCESSABLE_ENTITY,
                    PracticeConstants.PRACTICE_MOCK_TEST_BANK_INSUFFICIENT,
                    "Not enough practice questions for: " + String.join(", ", shortTypes));
        }

        lockSequence();
        Set<UUID> alreadyPinned = new HashSet<>();
        mockTestItemRepository.findAll().forEach(item -> alreadyPinned.add(item.getQuestionPublicId()));
        Map<String, PracticeTimingService.Timing> timings = timingService.activeTimings();
        int next = mockTestRepository.findMaxSequenceNo();
        List<PracticeMockTestResponse> created = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            next++;
            created.add(assembler.createTest(next, template, plan, candidates, alreadyPinned, timings));
        }
        return created;
    }

    /**
     * Keeps the number of tests in step with the bank: there should be as many tests as the practice pool can fill
     * without repeating a question (capped), and any missing ones are built now. Nothing is ever removed, so a test a
     * student has taken stays. No active template, or a bank too small for one test, simply means no new tests.
     */
    @Transactional
    public void ensureCapacity() {
        ExamDefinitionResponse template = scoreTemplateService.findActive().orElse(null);
        if (template == null) {
            return;
        }
        List<ExamDefinitionItemResponse> plan = orderedTemplateItems(template);
        int target = capacity(plan);
        if (plan.isEmpty() || mockTestRepository.countByDeletedFalse() >= target) {
            return;
        }
        lockSequence();
        // Re-read under the lock: another request may have built the missing tests meanwhile.
        int missing = target - (int) mockTestRepository.countByDeletedFalse();
        if (missing > 0) {
            generate(missing);
        }
    }

    /** How many tests the practice pool can fill with no question used twice, at most the auto-build cap. */
    int capacity(List<ExamDefinitionItemResponse> plan) {
        if (plan.isEmpty()) {
            return 0;
        }
        Map<String, Long> available = questionService.countPublishedByTaskTypeKeys(
                plan.stream().map(PracticeMockTestService::typeCode).toList());
        long fit = plan.stream()
                .mapToLong(item -> available.getOrDefault(typeCode(item), 0L) / item.minCount())
                .min().orElse(0);
        return (int) Math.min(fit, PracticeConstants.PRACTICE_MOCK_TEST_AUTO_LIMIT);
    }


    /** Lists the published tests with the student's latest non-discarded attempt at each. */
    @Transactional(readOnly = true)
    public List<PracticeMockTestResponse> listForStudent(UUID studentPublicId) {
        List<PracticeMockTest> tests = mockTestRepository
                .findByStatusAndDeletedFalseOrderBySequenceNoAsc(PracticeMockTestStatus.PUBLISHED);
        if (tests.isEmpty()) {
            return List.of();
        }
        Map<Long, PracticeSession> latestByTest = new LinkedHashMap<>();
        for (PracticeSession session : sessionRepository.findMockTestAttempts(studentPublicId,
                tests.stream().map(PracticeMockTest::getId).toList())) {
            latestByTest.putIfAbsent(session.getMockTestId(), session); // newest first
        }
        Map<Long, List<PracticeResponse>> itemsBySession = latestByTest.isEmpty() ? Map.of()
                : sessionItemRepository.findByPracticeSessionIdInAndDeletedFalse(
                        latestByTest.values().stream().map(PracticeSession::getId).toList()).stream()
                .collect(Collectors.groupingBy(PracticeResponse::getPracticeSessionId));
        Map<UUID, ExamDefinitionResponse> templates = templatesFor(tests);
        return tests.stream().map(test -> {
            PracticeSession attempt = latestByTest.get(test.getId());
            return toResponse(test, attempt == null ? null
                    : attemptOf(attempt, itemsBySession.getOrDefault(attempt.getId(), List.of()),
                            templates.get(test.getTemplatePublicId())));
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<PracticeMockTestResponse> listForAdmin() {
        return mockTestRepository.findByDeletedFalseOrderBySequenceNoAsc().stream()
                .map(test -> toResponse(test, null)).toList();
    }

    /** The published, pinned test of a public id; students cannot reach hidden or removed tests. */
    @Transactional(readOnly = true)
    public PracticeMockTest requirePublished(UUID publicId) {
        return mockTestRepository.findByPublicIdAndDeletedFalse(publicId)
                .filter(test -> test.getStatus() == PracticeMockTestStatus.PUBLISHED)
                .orElseThrow(() -> new PracticeSessionException(HttpStatus.NOT_FOUND,
                        PracticeConstants.PRACTICE_MOCK_TEST_NOT_FOUND,
                        PracticeConstants.PRACTICE_MOCK_TEST_NOT_FOUND_MESSAGE));
    }

    @Transactional(readOnly = true)
    public List<PracticeMockTestItem> itemsOf(Long mockTestId) {
        return mockTestItemRepository.findByMockTestIdOrderByOrderIndexAsc(mockTestId);
    }

    /** Estimated scores of a finished attempt, with the template the test was built from (or the active one). */
    public com.pte.practice.internal.dto.response.mocktest.PracticeEstimatedScoresResponse estimateFor(
            List<PracticeResponse> items, ExamDefinitionResponse template) {
        return estimator.estimate(items.stream().map(PracticeMockTestService::toScoredItem).toList(), template.items());
    }

    @Transactional(readOnly = true)
    public ExamDefinitionResponse templateForSession(Long mockTestId) {
        return mockTestRepository.findById(mockTestId).map(this::templateFor).orElse(null);
    }

    /** Template a test was built from, or the active one if that template is gone. */
    public ExamDefinitionResponse templateFor(PracticeMockTest test) {
        if (test.getTemplatePublicId() != null) {
            try {
                return scoreTemplateService.getByPublicId(test.getTemplatePublicId());
            } catch (RuntimeException ex) {
                // fall through to the active template
            }
        }
        return scoreTemplateService.findActive().orElse(null);
    }

    private Map<UUID, ExamDefinitionResponse> templatesFor(Collection<PracticeMockTest> tests) {
        Set<UUID> ids = tests.stream().map(PracticeMockTest::getTemplatePublicId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        return scoreTemplateService.getByPublicIds(ids);
    }

    private PracticeMockTestResponse.LatestAttempt attemptOf(PracticeSession session, List<PracticeResponse> items,
            ExamDefinitionResponse template) {
        int answered = (int) items.stream().filter(item -> item.getStatus() == PracticeResponseStatus.ANSWERED).count();
        Integer overall = null;
        if (session.getStatus() == PracticeSessionStatus.COMPLETED && template != null) {
            overall = estimateFor(items, template).overall();
        }
        return new PracticeMockTestResponse.LatestAttempt(session.getPublicId(), session.getStatus().name(),
                answered, items.size(), overall);
    }

    static PracticeScoreEstimator.ScoredItem toScoredItem(PracticeResponse item) {
        if (item.getStatus() != PracticeResponseStatus.ANSWERED) {
            return PracticeScoreEstimator.ScoredItem.of(item.getTaskCode(), PracticeScoreEstimator.ItemOutcome.MISSING);
        }
        Integer score = item.getAiScore() != null ? item.getAiScore() : item.getObjectiveScore();
        if (score != null) {
            return PracticeScoreEstimator.ScoredItem.scored(item.getTaskCode(), score);
        }
        boolean failed = item.getScoringStatus() == com.pte.practice.internal.domain.enums.PracticeScoringStatus.FAILED;
        return PracticeScoreEstimator.ScoredItem.of(item.getTaskCode(), failed
                ? PracticeScoreEstimator.ItemOutcome.FAILED : PracticeScoreEstimator.ItemOutcome.SCORING);
    }


    private static List<ExamDefinitionItemResponse> orderedTemplateItems(ExamDefinitionResponse template) {
        return template.items().stream()
                .filter(item -> item.minCount() > 0)
                .sorted(Comparator.comparingInt((ExamDefinitionItemResponse item) -> sectionRank(item.section()))
                        .thenComparingInt(ExamDefinitionItemResponse::sequence))
                .toList();
    }

    private static int sectionRank(String section) {
        int rank = section == null ? -1 : SECTION_ORDER.indexOf(section.toUpperCase());
        return rank < 0 ? SECTION_ORDER.size() : rank;
    }

    private static String typeCode(ExamDefinitionItemResponse item) {
        return PracticeTimingService.normalize(item.taskTypeKey() == null ? item.taskType() : item.taskTypeKey());
    }


    /** Serializes test numbering across requests and instances; the unique sequence number is the backstop. */
    private void lockSequence() {
        if (entityManager == null) {
            return; // focused tests build the service without a persistence context
        }
        Session session = entityManager.unwrap(Session.class);
        boolean postgres = session.doReturningWork(connection ->
                connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgres"));
        if (postgres) {
            entityManager.createNativeQuery("select count(*) from (select pg_advisory_xact_lock(cast(:key as bigint))) t")
                    .setParameter("key", PracticeConstants.PRACTICE_MOCK_TEST_ADVISORY_LOCK).getSingleResult();
        }
    }
}
