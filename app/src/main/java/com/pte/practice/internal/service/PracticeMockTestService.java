package com.pte.practice.internal.service;

import com.pte.itembank.PracticeQuestionService;
import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.practice.internal.domain.PracticeMockTest;
import com.pte.practice.internal.domain.PracticeMockTestItem;
import com.pte.practice.internal.domain.PracticeSession;
import com.pte.practice.internal.domain.PracticeSessionItem;
import com.pte.practice.internal.domain.enums.PracticeMockTestStatus;
import com.pte.practice.internal.domain.enums.PracticeSessionItemStatus;
import com.pte.practice.internal.domain.enums.PracticeSessionStatus;
import com.pte.practice.internal.dto.response.PracticeMockTestResponse;
import com.pte.practice.internal.exception.PracticeSessionException;
import com.pte.practice.internal.repository.PracticeMockTestItemRepository;
import com.pte.practice.internal.repository.PracticeMockTestRepository;
import com.pte.practice.internal.repository.PracticeSessionItemRepository;
import com.pte.practice.internal.repository.PracticeSessionRepository;
import com.pte.scoretemplate.ScoreTemplateService;
import com.pte.scoretemplate.dto.response.ScoreTemplateItemResponse;
import com.pte.scoretemplate.dto.response.ScoreTemplateResponse;
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
import java.util.Random;
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

    private static final int ESTIMATE_ROUNDING_MINUTES = 5;
    private static final List<String> SECTION_ORDER = List.of("SPEAKING", "WRITING", "READING", "LISTENING");

    private final PracticeMockTestRepository mockTestRepository;
    private final PracticeMockTestItemRepository mockTestItemRepository;
    private final PracticeSessionRepository sessionRepository;
    private final PracticeSessionItemRepository sessionItemRepository;
    private final PracticeQuestionService questionService;
    private final PracticeQuestionPicker questionPicker;
    private final ScoreTemplateService scoreTemplateService;
    private final PracticeTimingService timingService;
    private final PracticeScoreEstimator estimator;

    @PersistenceContext
    private EntityManager entityManager;

    public PracticeMockTestService(PracticeMockTestRepository mockTestRepository,
            PracticeMockTestItemRepository mockTestItemRepository, PracticeSessionRepository sessionRepository,
            PracticeSessionItemRepository sessionItemRepository, PracticeQuestionService questionService,
            PracticeQuestionPicker questionPicker, ScoreTemplateService scoreTemplateService,
            PracticeTimingService timingService, PracticeScoreEstimator estimator) {
        this.mockTestRepository = mockTestRepository;
        this.mockTestItemRepository = mockTestItemRepository;
        this.sessionRepository = sessionRepository;
        this.sessionItemRepository = sessionItemRepository;
        this.questionService = questionService;
        this.questionPicker = questionPicker;
        this.scoreTemplateService = scoreTemplateService;
        this.timingService = timingService;
        this.estimator = estimator;
    }

    /**
     * Builds {@code count} new tests, numbered after the existing ones. All or nothing: when the bank has too few
     * questions for any task type, nothing is created and the error names the types that are short.
     */
    @Transactional
    public List<PracticeMockTestResponse> generate(int count) {
        ScoreTemplateResponse template = scoreTemplateService.findActive().orElseThrow(() -> new PracticeSessionException(
                HttpStatus.UNPROCESSABLE_ENTITY, PracticeConstants.PRACTICE_MOCK_TEST_TEMPLATE_MISSING,
                PracticeConstants.PRACTICE_MOCK_TEST_TEMPLATE_MISSING_MESSAGE));
        List<ScoreTemplateItemResponse> plan = orderedTemplateItems(template);
        Map<String, List<UUID>> candidates = new LinkedHashMap<>();
        List<String> shortTypes = new ArrayList<>();
        for (ScoreTemplateItemResponse item : plan) {
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
            created.add(createTest(next, template, plan, candidates, alreadyPinned, timings));
        }
        return created;
    }

    private PracticeMockTestResponse createTest(int sequenceNo, ScoreTemplateResponse template,
            List<ScoreTemplateItemResponse> plan, Map<String, List<UUID>> candidates, Set<UUID> alreadyPinned,
            Map<String, PracticeTimingService.Timing> timings) {
        PracticeMockTest test = new PracticeMockTest();
        test.setSequenceNo(sequenceNo);
        test.setTitle(PracticeConstants.PRACTICE_MOCK_TEST_TITLE_PREFIX + sequenceNo);
        test.setSeed(sequenceNo);
        test.setTemplatePublicId(template.publicId());
        test.setTemplateVersion(template.version());
        test.setStatus(PracticeMockTestStatus.PUBLISHED);

        List<PracticeMockTestItem> items = new ArrayList<>();
        int seconds = 0;
        for (ScoreTemplateItemResponse templateItem : plan) {
            String code = typeCode(templateItem);
            // Seeded per test and type so the same test number always draws the same questions.
            Random random = new Random(sequenceNo * 1_000_003L + code.hashCode());
            List<UUID> picked = questionPicker.pick(candidates.get(code), alreadyPinned, templateItem.minCount(), random);
            PracticeTimingService.Timing timing = timings.get(PracticeTimingService.normalize(code));
            for (UUID questionId : picked) {
                PracticeMockTestItem item = new PracticeMockTestItem();
                item.setQuestionPublicId(questionId);
                item.setTaskCode(code);
                item.setSection(templateItem.section());
                item.setOrderIndex(items.size());
                items.add(item);
                alreadyPinned.add(questionId);
                if (timing != null) {
                    seconds += (timing.preListenSeconds() == null ? 0 : timing.preListenSeconds())
                            + timing.preRecordSeconds() + timing.responseSeconds();
                }
            }
        }
        test.setItemCount(items.size());
        test.setEstimatedMinutes(roundedMinutes(seconds));
        PracticeMockTest saved = mockTestRepository.saveAndFlush(test);
        items.forEach(item -> item.setMockTestId(saved.getId()));
        mockTestItemRepository.saveAll(items);
        return toResponse(saved, null);
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
        Map<Long, List<PracticeSessionItem>> itemsBySession = latestByTest.isEmpty() ? Map.of()
                : sessionItemRepository.findByPracticeSessionIdInAndDeletedFalse(
                        latestByTest.values().stream().map(PracticeSession::getId).toList()).stream()
                .collect(Collectors.groupingBy(PracticeSessionItem::getPracticeSessionId));
        Map<UUID, ScoreTemplateResponse> templates = templatesFor(tests);
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
    public com.pte.practice.internal.dto.response.PracticeEstimatedScoresResponse estimateFor(
            List<PracticeSessionItem> items, ScoreTemplateResponse template) {
        return estimator.estimate(items.stream().map(PracticeMockTestService::toScoredItem).toList(), template.items());
    }

    @Transactional(readOnly = true)
    public ScoreTemplateResponse templateForSession(Long mockTestId) {
        return mockTestRepository.findById(mockTestId).map(this::templateFor).orElse(null);
    }

    /** Template a test was built from, or the active one if that template is gone. */
    public ScoreTemplateResponse templateFor(PracticeMockTest test) {
        if (test.getTemplatePublicId() != null) {
            try {
                return scoreTemplateService.getByPublicId(test.getTemplatePublicId());
            } catch (RuntimeException ex) {
                // fall through to the active template
            }
        }
        return scoreTemplateService.findActive().orElse(null);
    }

    private Map<UUID, ScoreTemplateResponse> templatesFor(Collection<PracticeMockTest> tests) {
        Set<UUID> ids = tests.stream().map(PracticeMockTest::getTemplatePublicId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        return scoreTemplateService.getByPublicIds(ids);
    }

    private PracticeMockTestResponse.LatestAttempt attemptOf(PracticeSession session, List<PracticeSessionItem> items,
            ScoreTemplateResponse template) {
        int answered = (int) items.stream().filter(item -> item.getStatus() == PracticeSessionItemStatus.ANSWERED).count();
        Integer overall = null;
        if (session.getStatus() == PracticeSessionStatus.COMPLETED && template != null) {
            overall = estimateFor(items, template).overall();
        }
        return new PracticeMockTestResponse.LatestAttempt(session.getPublicId(), session.getStatus().name(),
                answered, items.size(), overall);
    }

    static PracticeScoreEstimator.ScoredItem toScoredItem(PracticeSessionItem item) {
        if (item.getStatus() != PracticeSessionItemStatus.ANSWERED) {
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

    private PracticeMockTestResponse toResponse(PracticeMockTest test, PracticeMockTestResponse.LatestAttempt attempt) {
        return new PracticeMockTestResponse(test.getPublicId(), test.getTitle(), test.getSequenceNo(),
                test.getItemCount(), test.getEstimatedMinutes(), attempt);
    }

    private static List<ScoreTemplateItemResponse> orderedTemplateItems(ScoreTemplateResponse template) {
        return template.items().stream()
                .filter(item -> item.minCount() > 0)
                .sorted(Comparator.comparingInt((ScoreTemplateItemResponse item) -> sectionRank(item.section()))
                        .thenComparingInt(ScoreTemplateItemResponse::sequence))
                .toList();
    }

    private static int sectionRank(String section) {
        int rank = section == null ? -1 : SECTION_ORDER.indexOf(section.toUpperCase());
        return rank < 0 ? SECTION_ORDER.size() : rank;
    }

    private static String typeCode(ScoreTemplateItemResponse item) {
        return PracticeTimingService.normalize(item.taskTypeKey() == null ? item.taskType() : item.taskTypeKey());
    }

    private static int roundedMinutes(int seconds) {
        int minutes = (int) Math.ceil(seconds / 60.0);
        int rounded = (int) Math.ceil(minutes / (double) ESTIMATE_ROUNDING_MINUTES) * ESTIMATE_ROUNDING_MINUTES;
        return Math.max(ESTIMATE_ROUNDING_MINUTES, rounded);
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
