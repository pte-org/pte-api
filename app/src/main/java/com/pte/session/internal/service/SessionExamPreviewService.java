package com.pte.session.internal.service;

import com.pte.assessment.AssessmentService;
import com.pte.assessment.dto.response.SnapshotContentResponse;
import com.pte.media.MediaService;
import com.pte.session.domain.ExamSession;
import com.pte.session.internal.dto.response.ExamPreviewResponse;
import com.pte.session.internal.exception.GenerationNotReadyException;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Builds a tenant-scoped host preview without exposing snapshot answer keys. */
@Service
public class SessionExamPreviewService {

    private static final long MEDIA_URL_TTL_SECONDS = 300;

    private final SessionLifecycleService sessionLifecycleService;
    private final AssessmentService assessmentService;
    private final MediaService mediaService;
    private final JsonMapper jsonMapper;

    public SessionExamPreviewService(SessionLifecycleService sessionLifecycleService,
            AssessmentService assessmentService, MediaService mediaService, JsonMapper jsonMapper) {
        this.sessionLifecycleService = sessionLifecycleService;
        this.assessmentService = assessmentService;
        this.mediaService = mediaService;
        this.jsonMapper = jsonMapper;
    }

    @Transactional(readOnly = true)
    public ExamPreviewResponse preview(UUID sessionPublicId, CurrentUser caller) {
        ExamSession session = sessionLifecycleService.findOwned(sessionPublicId, caller);
        if (session.getSnapshotPublicId() == null) {
            throw new GenerationNotReadyException();
        }

        SnapshotContentResponse snapshot = assessmentService.getFullContent(session.getSnapshotPublicId());
        Map<UUID, String> mediaUrls = new HashMap<>();
        List<ExamPreviewResponse.Item> items = snapshot.items().stream()
                .map(item -> toPreviewItem(item, session.getTenantId(), mediaUrls))
                .toList();
        return new ExamPreviewResponse(snapshot.publicId(), snapshot.name(), snapshot.version(), items);
    }

    private ExamPreviewResponse.Item toPreviewItem(SnapshotContentResponse.Item item, UUID tenantId,
            Map<UUID, String> mediaUrls) {
        return new ExamPreviewResponse.Item(
                item.orderIndex(),
                item.section(),
                item.taskType(),
                item.taskTypeCode(),
                item.taskTypeDisplayName(),
                item.title(),
                item.promptText(),
                resolveMedia(item.audioPromptRef(), tenantId, mediaUrls),
                resolveMedia(item.imagePromptRef(), tenantId, mediaUrls),
                item.minWordCount(),
                item.maxWordCount(),
                parseOptions(item.optionsJson()));
    }

    private String resolveMedia(UUID mediaPublicId, UUID tenantId, Map<UUID, String> cache) {
        if (mediaPublicId == null) {
            return null;
        }
        return cache.computeIfAbsent(mediaPublicId,
                id -> mediaService.presignGet(id, MEDIA_URL_TTL_SECONDS, tenantId).url());
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
            throw new IllegalStateException("Published exam options could not be read", exception);
        }
    }
}
