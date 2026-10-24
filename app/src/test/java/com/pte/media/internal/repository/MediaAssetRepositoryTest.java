package com.pte.media.internal.repository;

import com.pte.media.domain.MediaAsset;
import com.pte.media.domain.enums.MediaStatus;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Phase 1: MediaAsset replaces MediaObject; table/column names stay compatible with media references. */
@DataJpaTest
class MediaAssetRepositoryTest {

    @Autowired
    private MediaAssetRepository repository;

    @Test
    void keepsTheLegacyTableNameSoQuestionAndAnswerMediaRefsStayValid() {
        assertThat(MediaAsset.class.getAnnotation(Table.class).name()).isEqualTo("media_objects");
    }

    @Test
    void tenantScopedLookupHidesAnotherTenantsAssetButTrustedLookupSeesSharedOnes() {
        UUID tenantId = UUID.randomUUID();
        MediaAsset own = asset(tenantId);
        MediaAsset shared = asset(null);
        MediaAsset foreign = asset(UUID.randomUUID());
        repository.saveAllAndFlush(java.util.List.of(own, shared, foreign));

        assertThat(repository.findByPublicIdAndTenantId(own.getPublicId(), tenantId)).isPresent();
        assertThat(repository.findByPublicIdAndTenantId(foreign.getPublicId(), tenantId)).isEmpty();
        assertThat(repository.findAllForTrustedTenant(
                java.util.List.of(own.getPublicId(), shared.getPublicId(), foreign.getPublicId()), tenantId))
                .extracting(MediaAsset::getPublicId)
                .containsExactlyInAnyOrder(own.getPublicId(), shared.getPublicId());
    }

    @Test
    void markUploadedMovesStatusFromPendingToUploaded() {
        MediaAsset asset = asset(UUID.randomUUID());
        assertThat(asset.getStatus()).isEqualTo(MediaStatus.PENDING_UPLOAD);

        asset.markUploaded();

        assertThat(asset.getStatus()).isEqualTo(MediaStatus.UPLOADED);
    }

    private MediaAsset asset(UUID tenantId) {
        MediaAsset asset = new MediaAsset();
        asset.setPublicId(UUID.randomUUID());
        asset.setTenantId(tenantId);
        asset.setOwnerPublicId(UUID.randomUUID());
        asset.setContentType("audio/mpeg");
        asset.setStorageKey("pte/" + UUID.randomUUID());
        return asset;
    }
}
