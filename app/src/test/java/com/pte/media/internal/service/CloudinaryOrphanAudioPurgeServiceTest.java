package com.pte.media.internal.service;

import com.pte.media.domain.MediaAsset;
import com.pte.media.internal.constant.MediaConstants;
import com.pte.media.internal.repository.MediaAssetRepository;
import com.pte.shared.practice.PracticeMediaReferencePort;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CloudinaryOrphanAudioPurgeServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-12T03:45:00Z");

    private final MediaAssetRepository repository = mock(MediaAssetRepository.class);
    private final PracticeMediaReferencePort referencePort = mock(PracticeMediaReferencePort.class);
    private final CloudinaryAssetDestroyer destroyer = mock(CloudinaryAssetDestroyer.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private CloudinaryOrphanAudioPurgeService service(boolean configured) {
        return new CloudinaryOrphanAudioPurgeService(repository, referencePort, destroyer, "pte/test", configured,
                clock);
    }

    private static MediaAsset recording(long id) {
        MediaAsset asset = new MediaAsset();
        ReflectionTestUtils.setField(asset, "id", id);
        asset.setPublicId(UUID.randomUUID());
        asset.setPracticeItemPublicId(UUID.randomUUID());
        return asset;
    }

    @Test
    void removesOnlyRecordingsNoAnswerUsesAndKeepsSubmittedOnes() {
        MediaAsset submitted = recording(1);
        MediaAsset replacedDraft = recording(2);
        Instant cutoff = NOW.minus(MediaConstants.ORPHAN_AUDIO_GRACE);
        when(repository.findPracticeAudioPage(eq("pte/test/%"), eq(cutoff), eq(0L), any())).thenReturn(List.of(submitted, replacedDraft));
        when(repository.findPracticeAudioPage(eq("pte/test/%"), eq(cutoff), eq(2L), any())).thenReturn(List.of());
        when(referencePort.findReferencedResponseAudio(Map.of(
                submitted.getPublicId(), submitted.getPracticeItemPublicId(),
                replacedDraft.getPublicId(), replacedDraft.getPracticeItemPublicId())))
                .thenReturn(Set.of(submitted.getPublicId()));
        when(destroyer.destroy(replacedDraft)).thenReturn(true);

        assertThat(service(true).purgeOrphanPracticeAudio()).isEqualTo(1);
        verify(destroyer, never()).destroy(submitted);
        verify(repository).delete(replacedDraft);
        verify(repository, never()).delete(submitted);
    }

    @Test
    void keepsTheRowWhenCloudinaryDoesNotRemoveTheFile() {
        MediaAsset orphan = recording(5);
        when(repository.findPracticeAudioPage(eq("pte/test/%"), any(), eq(0L), any())).thenReturn(List.of(orphan));
        when(repository.findPracticeAudioPage(eq("pte/test/%"), any(), eq(5L), any())).thenReturn(List.of());
        when(referencePort.findReferencedResponseAudio(any())).thenReturn(Set.of());
        when(destroyer.destroy(orphan)).thenReturn(false);

        assertThat(service(true).purgeOrphanPracticeAudio()).isZero();
        verify(repository, never()).delete(any());
    }

    @Test
    void doesNothingWithoutCloudinaryCredentials() {
        assertThat(service(false).purgeOrphanPracticeAudio()).isZero();
        verify(repository, never()).findPracticeAudioPage(eq("pte/test/%"), any(), anyLong(), any());
        verifyNoInteractions(referencePort, destroyer);
    }
}
