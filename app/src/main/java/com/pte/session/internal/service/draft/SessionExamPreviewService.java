package com.pte.session.internal.service.draft;

import com.pte.assessment.AssessmentService;
import com.pte.assessment.dto.response.ResolvedExamItemResponse;
import com.pte.assessment.dto.response.SnapshotContentResponse;
import com.pte.session.domain.ExamSession;
import com.pte.session.internal.constant.SessionConstants;
import com.pte.session.internal.dto.response.ExamPreviewResponse;
import com.pte.session.internal.exception.draft.GenerationNotReadyException;
import com.pte.session.internal.service.SessionLifecycleService;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Builds a tenant-scoped, answer-free host preview from the shared runtime seam. */
@Service
public class SessionExamPreviewService {

    private static final long MEDIA_URL_TTL_SECONDS = 300;

    private final SessionLifecycleService sessionLifecycleService;
    private final AssessmentService assessmentService;
    private final JsonMapper jsonMapper;

    public SessionExamPreviewService(SessionLifecycleService sessionLifecycleService,
            AssessmentService assessmentService, JsonMapper jsonMapper) {
        this.sessionLifecycleService = sessionLifecycleService;
        this.assessmentService = assessmentService;
        this.jsonMapper = jsonMapper;
    }

    @Transactional(readOnly = true)
    public ExamPreviewResponse preview(UUID sessionPublicId, CurrentUser caller) {
        ExamSession session = sessionLifecycleService.findOwned(sessionPublicId, caller);
        if (session.getExamVersionPublicId() == null) {
            throw new GenerationNotReadyException();
        }

        SnapshotContentResponse snapshot = assessmentService.getFullContent(session.getExamVersionPublicId());
        List<ExamPreviewResponse.Item> items = assessmentService
                .resolveExamItems(snapshot, MEDIA_URL_TTL_SECONDS, session.getTenantId())
                .stream()
                .map(this::toPreviewItem)
                .toList();
        return new ExamPreviewResponse(snapshot.publicId(), snapshot.name(), snapshot.version(), items);
    }

    private ExamPreviewResponse.Item toPreviewItem(ResolvedExamItemResponse item) {
        return new ExamPreviewResponse.Item(
                item.orderIndex(),
                item.snapshotItemPublicId(),
                item.section(),
                item.taskType(),
                item.taskTypeCode(),
                item.taskTypeDisplayName(),
                item.title(),
                item.promptText(),
                item.audioUrl(),
                item.imageUrl(),
                item.minWordCount(),
                item.maxWordCount(),
                parseOptions(item.optionsJson()),
                item.sourceQuestionPublicId(),
                item.taskTypeKey(),
                item.runtime(),
                item.runtimeMappingVersion(),
                item.runtimeMappingStatus(),
                item.prepSeconds(),
                item.responseSeconds(),
                item.preListenSeconds(),
                item.preRecordSeconds(),
                item.audioDurationSeconds(),
                item.audioUrlExpiresAt(),
                item.imageUrlExpiresAt(),
                item.taskTypeInstruction());
    }

    private List<ExamPreviewResponse.Option> parseOptions(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = jsonMapper.readTree(optionsJson);
            if (root == null || !root.isArray()) {
                return List.of();
            }
            List<ExamPreviewResponse.Option> options = new ArrayList<>();
            for (JsonNode node : root) {
                JsonNode text = node.get("text");
                if (text == null || !text.isTextual()) {
                    continue;
                }
                JsonNode orderIndex = node.get("orderIndex");
                JsonNode blankIndex = node.get("blankIndex");
                options.add(new ExamPreviewResponse.Option(
                        orderIndex == null ? options.size() : orderIndex.asInt(options.size()),
                        blankIndex == null || blankIndex.isNull() ? null : blankIndex.asInt(),
                        text.asString()));
            }
            return List.copyOf(options);
        } catch (JacksonException exception) {
            throw new IllegalStateException(SessionConstants.PUBLISHED_EXAM_OPTIONS_READ_FAILED, exception);
        }
    }
}
