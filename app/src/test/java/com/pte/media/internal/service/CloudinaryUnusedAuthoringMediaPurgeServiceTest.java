package com.pte.media.internal.service;

import com.pte.media.domain.MediaAsset;
import com.pte.media.internal.repository.MediaAssetRepository;
import com.pte.shared.media.MediaReferenceSource;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CloudinaryUnusedAuthoringMediaPurgeServiceTest {

    private final MediaAssetRepository repository = mock(MediaAssetRepository.class);
    private final MediaReferenceSource questions = mock(MediaReferenceSource.class);
    private final MediaReferenceSource examVersions = mock(MediaReferenceSource.class);
    private final CloudinaryAssetDestroyer destroyer = mock(CloudinaryAssetDestroyer.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-12T04:15:00Z"), ZoneOffset.UTC);

    private CloudinaryUnusedAuthoringMediaPurgeService service(List<MediaReferenceSource> sources) {
        return new CloudinaryUnusedAuthoringMediaPurgeService(repository, sources, destroyer, "pte/test", true,
                clock);
    }

    private static MediaAsset file(long id) {
        MediaAsset asset = new MediaAsset();
        ReflectionTestUtils.setField(asset, "id", id);
        asset.setPublicId(UUID.randomUUID());
        return asset;
    }

    @Test
    void keepsAFileAnySourceNamesAndRemovesTheRest() {
        MediaAsset inQuestion = file(1);
        MediaAsset inExamVersion = file(2);
        MediaAsset abandoned = file(3);
        when(repository.findAuthoringMediaPage(eq("pte/test/%"), any(), eq(0L), any()))
                .thenReturn(List.of(inQuestion, inExamVersion, abandoned));
        when(repository.findAuthoringMediaPage(eq("pte/test/%"), any(), eq(3L), any())).thenReturn(List.of());
        when(questions.findReferencedMedia(any())).thenReturn(Set.of(inQuestion.getPublicId()));
        when(examVersions.findReferencedMedia(any())).thenReturn(Set.of(inExamVersion.getPublicId()));
        when(destroyer.destroy(abandoned)).thenReturn(true);

        assertThat(service(List.of(questions, examVersions)).purgeUnusedAuthoringMedia()).isEqualTo(1);
        verify(repository).delete(abandoned);
        verify(destroyer, never()).destroy(inQuestion);
        verify(destroyer, never()).destroy(inExamVersion);
    }

    @Test
    void withoutAnySourceNothingIsRemoved() {
        assertThat(service(List.of()).purgeUnusedAuthoringMedia()).isZero();
        verify(repository, never()).findAuthoringMediaPage(anyString(), any(), anyLong(), any());
        verifyNoInteractions(destroyer);
    }
}
