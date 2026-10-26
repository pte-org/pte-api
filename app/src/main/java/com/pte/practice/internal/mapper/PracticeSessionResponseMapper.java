package com.pte.practice.internal.mapper;

import com.pte.media.MediaService;
import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.practice.internal.domain.PracticeSession;
import com.pte.practice.internal.domain.PracticeResponse;
import com.pte.practice.internal.domain.enums.PracticeResponseStatus;
import com.pte.practice.internal.domain.enums.PracticeSessionKind;
import com.pte.practice.internal.domain.enums.PracticeSessionStatus;
import com.pte.practice.internal.dto.response.PracticeCatalogResponse;
import com.pte.practice.internal.dto.response.PracticeCatalogSectionResponse;
import com.pte.practice.internal.dto.response.PracticeItemTimingResponse;
import com.pte.practice.internal.dto.response.PracticeSessionResponse;
import com.pte.practice.internal.dto.response.PracticeTaskContentResponse;
import com.pte.practice.internal.dto.response.PracticeTaskResponse;
import com.pte.practice.internal.service.PracticeCatalogService;
import com.pte.practice.internal.service.PracticeResponseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Maps the session aggregate and its pinned items to the student API view. */
@Component
public class PracticeSessionResponseMapper {

    private static final Logger log = LoggerFactory.getLogger(PracticeSessionResponseMapper.class);

    private final PracticeCatalogService catalogService;
    private final PracticeResponseService itemService;
    private final JsonMapper jsonMapper;
    private final MediaService mediaService;

    public PracticeSessionResponseMapper(PracticeCatalogService catalogService,
            PracticeResponseService itemService) {
        this(catalogService, itemService, JsonMapper.builder().build(), null);
    }

    @Autowired
    public PracticeSessionResponseMapper(PracticeCatalogService catalogService,
            PracticeResponseService itemService, JsonMapper jsonMapper, MediaService mediaService) {
        this.catalogService = catalogService;
        this.itemService = itemService;
        this.jsonMapper = jsonMapper;
        this.mediaService = mediaService;
    }

    public PracticeSessionResponse toResponse(PracticeSession session) {
        PracticeCatalogResponse catalog = catalogService.getCatalog();
        Set<String> selected = splitCsv(session.getSelectedTaskTypes());
        List<PracticeCatalogSectionResponse> sections = selected.isEmpty()
                ? catalog.sections()
                : catalog.sections().stream()
                        .map(section -> new PracticeCatalogSectionResponse(section.code(), section.displayName(),
                                section.taskTypes().stream().filter(task -> selected.contains(task.code())).toList()))
                        .filter(section -> !section.taskTypes().isEmpty())
                        .toList();

        PracticeSessionStatus status = session.getStatus();
        List<PracticeResponse> items = itemService.itemsFor(session);
        int totalItemCount = items.isEmpty()
                ? (selected.isEmpty()
                        ? catalog.sections().stream().mapToInt(section -> section.taskTypes().size()).sum()
                        : session.effectiveKind() != PracticeSessionKind.TASK && session.getQuestionCount() != null
                                ? session.getQuestionCount()
                                : selected.size() * plannedQuestionsPerTask(session))
                : items.size();
        PracticeResponse currentItem = items.stream()
                .filter(item -> item.getStatus() == PracticeResponseStatus.PENDING)
                .findFirst()
                .orElse(null);

        return new PracticeSessionResponse(
                session.getPublicId(),
                session.getSourceType(),
                session.getProductCode(),
                session.getTitle(),
                session.getTenantId(),
                session.getCatalogVersion(),
                session.getTimeLimitSeconds(),
                status,
                versionOf(session),
                session.getStartedAt(),
                session.getDeadlineAt(),
                session.getCompletedAt(),
                session.getDiscardedAt(),
                status == PracticeSessionStatus.OVERVIEW || status == PracticeSessionStatus.IN_PROGRESS,
                status == PracticeSessionStatus.OVERVIEW || currentItem != null,
                status == PracticeSessionStatus.OVERVIEW || currentItem != null
                        ? PracticeConstants.PRACTICE_NEXT_ACTION : null,
                (int) items.stream().filter(item -> item.getStatus() == PracticeResponseStatus.ANSWERED).count(),
                totalItemCount,
                sections,
                currentItem == null ? null : toTaskResponse(currentItem, session.getTenantId()),
                null,
                session.effectiveTimingMode(),
                session.getQuestionCount(),
                session.effectiveKind().name(),
                session.getSkillCode(),
                selected.size() == 1 ? selected.iterator().next() : null);
    }

    private PracticeTaskResponse toTaskResponse(PracticeResponse item, UUID tenantId) {
        return new PracticeTaskResponse(
                item.getPublicId(),
                item.getOrderIndex(),
                item.getTaskCode(),
                item.getDisplayName(),
                item.getSection(),
                item.getRendererKey(),
                item.getContractVersion(),
                item.getAnswerSchemaVersion(),
                item.getStatus(),
                item.getSavedPayload(),
                item.getConfidence(),
                toContent(item, tenantId),
                toTiming(item));
    }

    /** Timing exists only on items of TIMED sessions; UNTIMED and legacy items carry none. */
    private PracticeItemTimingResponse toTiming(PracticeResponse item) {
        if (item.getResponseSeconds() == null) {
            return null;
        }
        return new PracticeItemTimingResponse(item.getPreListenSeconds(),
                item.getPreRecordSeconds() == null ? 0 : item.getPreRecordSeconds(), item.getResponseSeconds());
    }

    private PracticeTaskContentResponse toContent(PracticeResponse item, UUID tenantId) {
        if ((item.getPromptText() == null || item.getPromptText().isBlank())
                && (item.getOptionsJson() == null || item.getOptionsJson().isBlank())
                && item.getAudioPromptRef() == null && item.getImagePromptRef() == null) {
            return null;
        }
        List<PracticeTaskContentResponse.Option> options = List.of();
        if (item.getOptionsJson() != null && !item.getOptionsJson().isBlank()) {
            try {
                options = jsonMapper.readValue(item.getOptionsJson(),
                        new TypeReference<List<PracticeTaskContentResponse.Option>>() { });
            } catch (JacksonException ex) {
                throw new IllegalStateException("PRACTICE_CONTENT_SNAPSHOT_INVALID", ex);
            }
        }
        Map<UUID, String> mediaUrls = presignPromptMedia(item, tenantId);
        return new PracticeTaskContentResponse(item.getPromptText(), item.getMinWordCount(),
                item.getMaxWordCount(), item.getAudioPromptRef(), item.getImagePromptRef(), options,
                urlOf(mediaUrls, item.getAudioPromptRef()), urlOf(mediaUrls, item.getImagePromptRef()));
    }

    /** Immutable maps reject a null key, and a task may have no audio or no image. */
    private String urlOf(Map<UUID, String> mediaUrls, UUID ref) {
        return ref == null ? null : mediaUrls.get(ref);
    }

    /**
     * Students cannot resolve media refs themselves, so the prompt audio and image (and nothing else) are presigned
     * here. A media failure leaves the URL empty rather than failing the whole session read.
     */
    private Map<UUID, String> presignPromptMedia(PracticeResponse item, UUID tenantId) {
        Set<UUID> refs = new LinkedHashSet<>();
        if (item.getAudioPromptRef() != null) {
            refs.add(item.getAudioPromptRef());
        }
        if (item.getImagePromptRef() != null) {
            refs.add(item.getImagePromptRef());
        }
        if (refs.isEmpty() || mediaService == null) {
            return Map.of();
        }
        try {
            Map<UUID, String> urls = new HashMap<>();
            mediaService.presignGetAll(refs, PracticeConstants.PRACTICE_PROMPT_MEDIA_TTL_SECONDS, tenantId)
                    .forEach((id, presigned) -> urls.put(id, presigned.url()));
            return urls;
        } catch (RuntimeException ex) {
            log.warn("Practice prompt media could not be presigned for item {}", item.getPublicId(), ex);
            return Map.of();
        }
    }

    /** Upper bound shown before items exist; the real total may be lower when a pool has fewer questions. */
    private int plannedQuestionsPerTask(PracticeSession session) {
        return session.getQuestionCount() == null
                ? PracticeConstants.PRACTICE_LEGACY_QUESTION_COUNT : session.getQuestionCount();
    }

    private long versionOf(PracticeSession session) {
        return session.getVersion() == null ? 0L : session.getVersion();
    }

    private Set<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(csv.split(","))
                .filter(value -> !value.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }
}
