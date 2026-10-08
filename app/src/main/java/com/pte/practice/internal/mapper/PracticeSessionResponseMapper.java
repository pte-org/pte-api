package com.pte.practice.internal.mapper;

import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.practice.internal.domain.PracticeSession;
import com.pte.practice.internal.domain.PracticeSessionItem;
import com.pte.practice.internal.domain.enums.PracticeSessionItemStatus;
import com.pte.practice.internal.domain.enums.PracticeSessionStatus;
import com.pte.practice.internal.dto.response.PracticeCatalogResponse;
import com.pte.practice.internal.dto.response.PracticeCatalogSectionResponse;
import com.pte.practice.internal.dto.response.PracticeSessionResponse;
import com.pte.practice.internal.dto.response.PracticeTaskResponse;
import com.pte.practice.internal.service.PracticeCatalogService;
import com.pte.practice.internal.service.PracticeSessionItemService;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Maps the session aggregate and its pinned items to the student API view. */
@Component
public class PracticeSessionResponseMapper {

    private final PracticeCatalogService catalogService;
    private final PracticeSessionItemService itemService;

    public PracticeSessionResponseMapper(PracticeCatalogService catalogService,
            PracticeSessionItemService itemService) {
        this.catalogService = catalogService;
        this.itemService = itemService;
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
                item.getConfidence());
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
