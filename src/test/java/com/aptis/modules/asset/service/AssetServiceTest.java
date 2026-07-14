package com.aptis.modules.asset.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.aptis.common.exception.ApiException;
import com.aptis.common.exception.ErrorCode;
import com.aptis.modules.asset.domain.Asset;
import com.aptis.modules.asset.domain.AssetType;
import com.aptis.modules.asset.dto.request.CreateAssetRequest;
import com.aptis.modules.asset.dto.response.AssetResponse;
import com.aptis.modules.asset.repository.AssetRepository;

@ExtendWith(MockitoExtension.class)
class AssetServiceTest {

    @Mock
    private AssetRepository assetRepository;

    private AssetService assetService;

    @BeforeEach
    void setUp() {
        assetService = new AssetService(assetRepository);
    }

    @Test
    void createAssetSuccess() {
        // Arrange
        UUID uploadedBy = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(7);

        CreateAssetRequest request = new CreateAssetRequest(
            AssetType.AUDIO_QUESTION,
            "questions/listening/part_1.mp3",
            "https://cdn.aptis-lms.com/questions/listening/part_1.mp3",
            "part_1.mp3",
            1024L,
            "audio/mpeg",
            uploadedBy,
            tenantId,
            expiresAt
        );

        Asset mockSavedAsset = Asset.create(
            request.assetType(),
            request.storageKey(),
            request.cdnUrl(),
            request.filename(),
            request.sizeBytes(),
            request.mimeType(),
            request.uploadedBy(),
            request.tenantId(),
            request.expiresAt()
        );
        mockSavedAsset.setPublicId(UUID.randomUUID());

        when(assetRepository.save(any(Asset.class))).thenReturn(mockSavedAsset);

        // Act
        AssetResponse response = assetService.createAsset(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.publicId()).isEqualTo(mockSavedAsset.getPublicId());
        assertThat(response.assetType()).isEqualTo(AssetType.AUDIO_QUESTION);
        assertThat(response.storageKey()).isEqualTo("questions/listening/part_1.mp3");
        assertThat(response.cdnUrl()).isEqualTo("https://cdn.aptis-lms.com/questions/listening/part_1.mp3");
        assertThat(response.filename()).isEqualTo("part_1.mp3");
        assertThat(response.sizeBytes()).isEqualTo(1024L);
        assertThat(response.mimeType()).isEqualTo("audio/mpeg");
        assertThat(response.uploadedBy()).isEqualTo(uploadedBy);
        assertThat(response.tenantId()).isEqualTo(tenantId);
        assertThat(response.expiresAt()).isEqualTo(expiresAt);

        verify(assetRepository).save(any(Asset.class));
    }

    @Test
    void getAssetSuccess() {
        // Arrange
        UUID publicId = UUID.randomUUID();
        Asset mockAsset = new Asset();
        mockAsset.setPublicId(publicId);
        mockAsset.setAssetType(AssetType.IMAGE_QUESTION);
        mockAsset.setStorageKey("questions/speaking/prompt.png");
        mockAsset.setCdnUrl("https://cdn.aptis-lms.com/questions/speaking/prompt.png");
        mockAsset.setFilename("prompt.png");
        mockAsset.setSizeBytes(512L);
        mockAsset.setMimeType("image/png");

        when(assetRepository.findByPublicIdAndDeletedFalse(publicId)).thenReturn(Optional.of(mockAsset));

        // Act
        AssetResponse response = assetService.getAsset(publicId);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.publicId()).isEqualTo(publicId);
        assertThat(response.assetType()).isEqualTo(AssetType.IMAGE_QUESTION);
        assertThat(response.storageKey()).isEqualTo("questions/speaking/prompt.png");
        assertThat(response.cdnUrl()).isEqualTo("https://cdn.aptis-lms.com/questions/speaking/prompt.png");

        verify(assetRepository).findByPublicIdAndDeletedFalse(publicId);
    }

    @Test
    void getAssetNotFoundThrowsException() {
        // Arrange
        UUID publicId = UUID.randomUUID();
        when(assetRepository.findByPublicIdAndDeletedFalse(publicId)).thenReturn(Optional.empty());

        // Act & Assert
        ApiException exception = assertThrows(ApiException.class, () -> assetService.getAsset(publicId));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

        verify(assetRepository).findByPublicIdAndDeletedFalse(publicId);
    }

    @Test
    void deleteAssetSuccess() {
        // Arrange
        UUID publicId = UUID.randomUUID();
        Asset mockAsset = new Asset();
        mockAsset.setPublicId(publicId);
        mockAsset.setDeleted(false);

        when(assetRepository.findByPublicIdAndDeletedFalse(publicId)).thenReturn(Optional.of(mockAsset));
        when(assetRepository.save(mockAsset)).thenReturn(mockAsset);

        // Act
        assetService.deleteAsset(publicId);

        // Assert
        assertThat(mockAsset.getDeleted()).isTrue();

        verify(assetRepository).findByPublicIdAndDeletedFalse(publicId);
        verify(assetRepository).save(mockAsset);
    }

    @Test
    void deleteAssetNotFoundThrowsException() {
        // Arrange
        UUID publicId = UUID.randomUUID();
        when(assetRepository.findByPublicIdAndDeletedFalse(publicId)).thenReturn(Optional.empty());

        // Act & Assert
        ApiException exception = assertThrows(ApiException.class, () -> assetService.deleteAsset(publicId));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

        verify(assetRepository).findByPublicIdAndDeletedFalse(publicId);
    }
}
