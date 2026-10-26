package com.pte.practice.internal.service.mocktest;

import com.pte.assessment.dto.response.ExamDefinitionItemResponse;
import com.pte.assessment.dto.response.ExamDefinitionResponse;
import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.practice.internal.domain.PracticeMockTest;
import com.pte.practice.internal.domain.PracticeMockTestItem;
import com.pte.practice.internal.domain.enums.PracticeMockTestStatus;
import com.pte.practice.internal.dto.response.mocktest.PracticeMockTestResponse;
import com.pte.practice.internal.repository.PracticeMockTestItemRepository;
import com.pte.practice.internal.repository.PracticeMockTestRepository;
import com.pte.practice.internal.service.selection.PracticeQuestionPicker;
import com.pte.practice.internal.service.timing.PracticeTimingService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Persists a fixed mock test and its ordered question pins. */
final class PracticeMockTestAssembler {
    private static final int ESTIMATE_ROUNDING_MINUTES = 5;
    private final PracticeMockTestRepository mockTestRepository;
    private final PracticeMockTestItemRepository mockTestItemRepository;
    private final PracticeQuestionPicker questionPicker;

    PracticeMockTestAssembler(PracticeMockTestRepository mockTestRepository,
            PracticeMockTestItemRepository mockTestItemRepository, PracticeQuestionPicker questionPicker) {
        this.mockTestRepository = mockTestRepository;
        this.mockTestItemRepository = mockTestItemRepository;
        this.questionPicker = questionPicker;
    }

    PracticeMockTestResponse createTest(int sequenceNo, ExamDefinitionResponse template,
            List<ExamDefinitionItemResponse> plan, Map<String, List<UUID>> candidates, Set<UUID> alreadyPinned,
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
        for (ExamDefinitionItemResponse templateItem : plan) {
            String code = PracticeTimingService.normalize(templateItem.taskTypeKey() == null
                    ? templateItem.taskType() : templateItem.taskTypeKey());
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

    private static int roundedMinutes(int seconds) {
        int minutes = (int) Math.ceil(seconds / 60.0);
        int rounded = (int) Math.ceil(minutes / (double) ESTIMATE_ROUNDING_MINUTES) * ESTIMATE_ROUNDING_MINUTES;
        return Math.max(ESTIMATE_ROUNDING_MINUTES, rounded);
    }

    static PracticeMockTestResponse toResponse(PracticeMockTest test, PracticeMockTestResponse.LatestAttempt attempt) {
        return new PracticeMockTestResponse(test.getPublicId(), test.getTitle(), test.getSequenceNo(),
                test.getItemCount(), test.getEstimatedMinutes(), attempt);
    }
}
