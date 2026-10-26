package com.pte.attempt.internal.service;

import com.pte.assessment.dto.response.SnapshotContentResponse;
import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.internal.constant.AttemptConstants;
import com.pte.attempt.internal.service.cache.ExamContentCache;
import com.pte.attempt.internal.service.cache.ExamItemView;
import com.pte.itembank.TaskRuntimeProfileDescriptor;
import com.pte.itembank.TaskRuntimeProfileRegistry;
import com.pte.itembank.TaskTypeCodeCompatibility;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The attempt module's single door to the items of a frozen exam. Reads go through {@link ExamContentCache};
 * a miss rebuilds the view from the exam version, so an attempt never owns a copy of its exam. Presigned media
 * URLs are sized to the attempt's remaining time on a reload, and to the session window when the attempt starts.
 */
@Service
public class ExamContentService {

    private static final int CONTENT_CACHE_LIMIT = 256;

    private final ExamItemLoader loader;
    private final ExamContentCache cache;
    private final Clock clock;
    private final Map<UUID, Map<UUID, SnapshotContentResponse.Item>> contentItems = new ConcurrentHashMap<>();

    public ExamContentService(ExamItemLoader loader, ExamContentCache cache, Clock clock) {
        this.loader = loader;
        this.cache = cache;
        this.clock = clock;
    }

    /** Task identity of an exam item without content or media, for coverage, counts and tested sections. */
    public record ExamItemOutline(UUID publicId, int orderIndex, String section, String taskType,
            TaskRuntimeProfileDescriptor runtime) {
    }

    /** Header of the exam version, used before an attempt exists to read its definition and version. */
    public SnapshotContentResponse header(UUID examVersionPublicId) {
        return loader.content(examVersionPublicId);
    }

    /** Loads the exam for a starting attempt and warms the cache so the first task read is already a hit. */
    public List<ExamItemView> loadForStart(UUID attemptPublicId, SnapshotContentResponse content, UUID tenantId,
            Instant windowOpensAt, Instant windowClosesAt) {
        long mediaUrlTtlSeconds = Duration.between(windowOpensAt, windowClosesAt).getSeconds()
                + AttemptConstants.MEDIA_URL_GRACE_SECONDS;
        List<ExamItemView> items = loader.load(content, mediaUrlTtlSeconds, tenantId);
        cache.warm(attemptPublicId, items);
        return items;
    }

    public List<ExamItemView> items(ExamAttempt attempt) {
        return cache.getOrLoad(attempt.getPublicId(), () -> reload(attempt));
    }

    public Optional<ExamItemView> item(ExamAttempt attempt, UUID itemPublicId) {
        return items(attempt).stream().filter(item -> item.publicId().equals(itemPublicId)).findFirst();
    }

    public ExamItemView itemAt(ExamAttempt attempt, int orderIndex) {
        return items(attempt).stream().filter(item -> item.orderIndex() == orderIndex).findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        String.format(AttemptConstants.MISSING_ITEM_AT_INDEX, orderIndex)));
    }

    /** Items of an exam version keyed by item id, without media; the version is immutable, so it is memoized. */
    public Map<UUID, SnapshotContentResponse.Item> contentItems(UUID examVersionPublicId) {
        Map<UUID, SnapshotContentResponse.Item> cached = contentItems.get(examVersionPublicId);
        if (cached != null) {
            return cached;
        }
        Map<UUID, SnapshotContentResponse.Item> built = new LinkedHashMap<>();
        loader.content(examVersionPublicId).items().stream()
                .sorted(Comparator.comparingInt(SnapshotContentResponse.Item::orderIndex))
                .forEach(item -> built.put(item.itemPublicId(), item));
        if (contentItems.size() >= CONTENT_CACHE_LIMIT) {
            contentItems.clear();
        }
        Map<UUID, SnapshotContentResponse.Item> immutable = Collections.unmodifiableMap(built);
        contentItems.put(examVersionPublicId, immutable);
        return immutable;
    }

    /** Content-free outline of an exam version, in order, for coverage, counts and tested sections. */
    public List<ExamItemOutline> outline(UUID examVersionPublicId) {
        return contentItems(examVersionPublicId).values().stream()
                .map(item -> new ExamItemOutline(item.itemPublicId(), item.orderIndex(), item.section(),
                        TaskTypeCodeCompatibility.normalizeTaskTypeKey(item.taskTypeKey() == null
                                ? item.taskType() : item.taskTypeKey()), effectiveRuntime(item)))
                .toList();
    }

    /**
     * The runtime contract of an exam item: its own, or for an exam built before contracts were frozen on items the
     * standard contract of its task type. A custom task type without one has none.
     */
    public static TaskRuntimeProfileDescriptor effectiveRuntime(SnapshotContentResponse.Item item) {
        if (item.runtime() != null) {
            return item.runtime();
        }
        String taskType = TaskTypeCodeCompatibility.normalizeTaskTypeKey(
                item.taskTypeKey() == null ? item.taskType() : item.taskTypeKey());
        return TaskTypeCodeCompatibility.isStandard(taskType) ? TaskRuntimeProfileRegistry.descriptorFor(taskType) : null;
    }

    private List<ExamItemView> reload(ExamAttempt attempt) {
        long remainingSeconds = attempt.getExamEndTime() == null ? 0L
                : Math.max(0L, Duration.between(clock.instant(), attempt.getExamEndTime()).getSeconds());
        return loader.load(loader.content(attempt.getExamVersionPublicId()),
                remainingSeconds + AttemptConstants.MEDIA_URL_RELOAD_MARGIN_SECONDS, attempt.getTenantId());
    }
}
