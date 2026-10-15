package com.pte.practice.internal.mapper;

import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.practice.internal.domain.PracticeSession;
import com.pte.practice.internal.domain.PracticeSessionItem;
import com.pte.practice.internal.domain.enums.PracticeSessionItemStatus;
import com.pte.practice.internal.domain.enums.PracticeSessionStatus;
import com.pte.practice.internal.dto.response.PracticeCatalogResponse;
import com.pte.practice.internal.dto.response.PracticeCatalogSectionResponse;
import com.pte.practice.internal.dto.response.PracticeSessionResponse;
import com.pte.practice.internal.dto.response.PracticeTaskContentResponse;
import com.pte.practice.internal.dto.response.PracticeTaskResponse;
import com.pte.practice.internal.service.PracticeCatalogService;
import com.pte.practice.internal.service.PracticeSessionItemService;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Maps the session aggregate and its pinned items to the student API view. */
@Component
public class PracticeSessionResponseMapper {

    private final PracticeCatalogService catalogService;
    private final PracticeSessionItemService itemService;
    private final JsonMapper jsonMapper;

    public PracticeSessionResponseMapper(PracticeCatalogService catalogService,
            PracticeSessionItemService itemService) {
        this(catalogService, itemService, JsonMapper.builder().build());
    }

    @Autowired
    public PracticeSessionResponseMapper(PracticeCatalogService catalogService,
            PracticeSessionItemService itemService, JsonMapper jsonMapper) {
        this.catalogService = catalogService;
        this.itemService = itemService;
        this.jsonMapper = jsonMapper;
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
        List<PracticeSessionItem> items = itemService.itemsFor(session);
        int totalItemCount = items.isEmpty()
                ? (selected.isEmpty()
                        ? catalog.sections().stream().mapToInt(section -> section.taskTypes().size()).sum()
                        : selected.size())
                : items.size();
        PracticeSessionItem currentItem = items.stream()
                .filter(item -> item.getStatus() == PracticeSessionItemStatus.PENDING)
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
                (int) items.stream().filter(item -> item.getStatus() == PracticeSessionItemStatus.ANSWERED).count(),
                totalItemCount,
                sections,
                currentItem == null ? null : toTaskResponse(currentItem));
    }

    private PracticeTaskResponse toTaskResponse(PracticeSessionItem item) {
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
                toContent(item));
    }

    private PracticeTaskContentResponse toContent(PracticeSessionItem item) {
        if ((item.getPromptText() == null || item.getPromptText().isBlank())
                && (item.getOptionsJson() == null || item.getOptionsJson().isBlank())) {
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
        return new PracticeTaskContentResponse(item.getPromptText(), item.getMinWordCount(),
                item.getMaxWordCount(), options);
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
