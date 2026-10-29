package com.pte.attempt.internal.service;

import com.pte.assessment.AssessmentService;
import com.pte.assessment.ExamDefinitionService;
import com.pte.assessment.dto.response.ResolvedExamItemResponse;
import com.pte.assessment.dto.response.SnapshotContentResponse;
import com.pte.assessment.internal.config.TaskTimingConfig;
import com.pte.assessment.internal.service.ExamItemRuntimeResolver;
import com.pte.attempt.internal.service.cache.ExamItemView;
import com.pte.media.MediaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Adapts the assessment-owned resolved item contract into the attempt module's
 * answer-bearing cache view. Runtime/timing/media resolution is deliberately
 * shared with host preview through {@link AssessmentService}.
 */
@Component
class ExamItemLoader {

    private final AssessmentService assessmentService;
    private final ExamItemRuntimeResolver testResolver;

    @Autowired
    ExamItemLoader(AssessmentService assessmentService) {
        this.assessmentService = assessmentService;
        this.testResolver = null;
    }

    /** Source-compatible constructor for focused loader tests. */
    ExamItemLoader(AssessmentService assessmentService, MediaService mediaService,
            TaskTimingConfig taskTimingConfig, ExamDefinitionService examDefinitionService,
            Clock clock) {
        this.assessmentService = assessmentService;
        this.testResolver = new ExamItemRuntimeResolver(mediaService, taskTimingConfig, examDefinitionService, clock);
    }

    /** The exam version's own header, for callers that need its definition and version before the items. */
    SnapshotContentResponse content(UUID examVersionPublicId) {
        return assessmentService.getFullContent(examVersionPublicId);
    }

    List<ExamItemView> load(SnapshotContentResponse content, long mediaUrlTtlSeconds, UUID tenantId) {
        List<ResolvedExamItemResponse> resolved = testResolver == null
                ? assessmentService.resolveExamItems(content, mediaUrlTtlSeconds, tenantId)
                : testResolver.resolve(content, mediaUrlTtlSeconds, tenantId);
        return resolved.stream().map(this::toView).toList();
    }

    private ExamItemView toView(ResolvedExamItemResponse item) {
        return new ExamItemView(
                item.snapshotItemPublicId(),
                item.orderIndex(),
                item.section(),
                item.taskType(),
                item.title(),
                item.promptText(),
                item.audioPromptRef(),
                item.imagePromptRef(),
                item.referenceAnswerText(),
                item.correctAnswerText(),
                item.minWordCount(),
                item.maxWordCount(),
                item.optionsJson(),
                item.prepSeconds(),
                item.responseSeconds(),
                item.preListenSeconds(),
                item.preRecordSeconds(),
                item.imageUrl(),
                item.audioUrl(),
                item.audioUrlExpiresAt(),
                item.taskTypeCode(),
                item.runtime(),
                item.runtimeMappingVersion(),
                item.runtimeMappingStatus(),
                item.taskTypeDisplayName());
    }
}
