package com.pte.attempt.internal.service;

import com.pte.assessment.dto.response.SnapshotContentResponse;
import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.internal.service.cache.ExamContentCache;
import com.pte.attempt.internal.service.cache.ExamItemView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamContentServiceTest {

    @Mock
    private ExamItemLoader loader;

    @Mock
    private ExamContentCache cache;

    private ExamContentService service;
    private final UUID versionId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID attemptId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ExamContentService(loader, cache, com.pte.TestClocks.fixed());
    }

    private SnapshotContentResponse.Item contentItem(int orderIndex, String section, String taskType) {
        return new SnapshotContentResponse.Item(orderIndex, section, taskType, "t", "p", null, null, null, null,
                null, null, null, taskType, "t", taskType, null, null, null, null, UUID.randomUUID());
    }

    private SnapshotContentResponse content(SnapshotContentResponse.Item... items) {
        return new SnapshotContentResponse(versionId, "exam", 1, UUID.randomUUID(), 1, UUID.randomUUID(),
                List.of(items));
    }

    private ExamAttempt attempt() {
        ExamAttempt attempt = new ExamAttempt();
        attempt.setSessionPublicId(sessionId);
        attempt.setPublicId(attemptId);
        attempt.setTenantId(UUID.randomUUID());
        attempt.setExamVersionPublicId(versionId);
        attempt.setExamEndTime(Instant.now().plusSeconds(600));
        return attempt;
    }

    @Test
    void outline_isOrderedAndCarriesTaskIdentityWithoutContent() {
        SnapshotContentResponse.Item second = contentItem(1, "WRITING", "WRITE_ESSAY");
        SnapshotContentResponse.Item first = contentItem(0, "SPEAKING", "READ_ALOUD");
        when(loader.content(versionId)).thenReturn(content(second, first));

        List<ExamContentService.ExamItemOutline> outline = service.outline(versionId);

        assertThat(outline).extracting(ExamContentService.ExamItemOutline::orderIndex).containsExactly(0, 1);
        assertThat(outline).extracting(ExamContentService.ExamItemOutline::taskType)
                .containsExactly("READ_ALOUD", "WRITE_ESSAY");
        assertThat(outline.get(0).publicId()).isEqualTo(first.itemPublicId());
        assertThat(outline.get(1).section()).isEqualTo("WRITING");
    }

    @Test
    void contentItems_areKeyedByItemIdAndLoadedOncePerVersion() {
        SnapshotContentResponse.Item item = contentItem(0, "READING", "MC_READING_SINGLE");
        when(loader.content(versionId)).thenReturn(content(item));

        Map<UUID, SnapshotContentResponse.Item> first = service.contentItems(versionId);
        Map<UUID, SnapshotContentResponse.Item> second = service.contentItems(versionId);

        assertThat(first).containsOnlyKeys(item.itemPublicId());
        assertThat(second).isSameAs(first);
        verify(loader, times(1)).content(versionId);
    }

    @Test
    void items_areReadThroughTheSessionCache() {
        ExamItemView item = FakeExam.view(0, "READING", "MC_READING_SINGLE", 0, 60);
        when(cache.getOrLoad(eq(attemptId), any())).thenReturn(List.of(item));

        ExamAttempt attempt = attempt();

        assertThat(service.items(attempt)).containsExactly(item);
        assertThat(service.item(attempt, item.publicId())).contains(item);
        assertThat(service.item(attempt, UUID.randomUUID())).isEmpty();
        assertThat(service.itemAt(attempt, 0)).isEqualTo(item);
        assertThatThrownBy(() -> service.itemAt(attempt, 5)).isInstanceOf(IllegalStateException.class);
    }

    @SuppressWarnings("unchecked")
    @Test
    void aCacheMiss_reloadsFromTheExamVersionWithMediaSizedToTheRemainingTime() {
        SnapshotContentResponse content = content(contentItem(0, "READING", "MC_READING_SINGLE"));
        ExamItemView item = FakeExam.view(0, "READING", "MC_READING_SINGLE", 0, 60);
        ExamAttempt attempt = attempt();
        when(cache.getOrLoad(eq(attemptId), any())).thenAnswer(invocation -> {
            Supplier<List<ExamItemView>> miss = invocation.getArgument(1);
            return miss.get();
        });
        when(loader.content(versionId)).thenReturn(content);
        when(loader.load(eq(content), anyLong(), eq(attempt.getTenantId()))).thenReturn(List.of(item));

        assertThat(service.items(attempt)).containsExactly(item);

        verify(loader).load(eq(content), org.mockito.ArgumentMatchers.longThat(ttl -> ttl > 600 && ttl <= 1300),
                eq(attempt.getTenantId()));
    }

    @Test
    void loadForStart_warmsTheCacheSoTheFirstTaskReadIsAHit() {
        SnapshotContentResponse content = content(contentItem(0, "READING", "MC_READING_SINGLE"));
        ExamItemView item = FakeExam.view(0, "READING", "MC_READING_SINGLE", 0, 60);
        UUID tenantId = UUID.randomUUID();
        Instant opensAt = Instant.now();
        Instant closesAt = opensAt.plusSeconds(3600);
        when(loader.load(eq(content), eq(3660L), eq(tenantId))).thenReturn(List.of(item));

        List<ExamItemView> loaded = service.loadForStart(attemptId, content, tenantId, opensAt, closesAt);

        assertThat(loaded).containsExactly(item);
        verify(cache).warm(attemptId, List.of(item));
    }
}
