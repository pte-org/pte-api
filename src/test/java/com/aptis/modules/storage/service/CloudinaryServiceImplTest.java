package com.aptis.modules.storage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.aptis.modules.storage.constant.StorageConstants;
import com.aptis.modules.storage.domain.exception.StorageException;
import com.aptis.modules.storage.dto.response.UploadResponse;
import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;

@ExtendWith(MockitoExtension.class)
class CloudinaryServiceImplTest {

    @Mock
    private Cloudinary cloudinary;

    @Mock
    private Uploader uploader;

    private CloudinaryServiceImpl cloudinaryService;

    @BeforeEach
    void setUp() {
        cloudinaryService = new CloudinaryServiceImpl(cloudinary);
    }

    @SuppressWarnings("unchecked")
    @Test
    void uploadFileSuccess() throws IOException {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "test-image.png",
            "image/png",
            "test content".getBytes()
        );
        String folder = "questions";

        Map<String, Object> mockUploadResult = Map.of(
            "secure_url", "https://res.cloudinary.com/demo/image/upload/v123/questions/test.png",
            "public_id", "questions/test"
        );

        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(), any(Map.class))).thenReturn(mockUploadResult);

        // Act
        UploadResponse response = cloudinaryService.uploadFile(file, folder);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.storageKey()).isEqualTo("questions/test");
        assertThat(response.cdnUrl()).isEqualTo("https://res.cloudinary.com/demo/image/upload/v123/questions/test.png");
        assertThat(response.filename()).isEqualTo("test-image.png");
        assertThat(response.sizeBytes()).isEqualTo(file.getSize());
        assertThat(response.mimeType()).isEqualTo("image/png");

        verify(cloudinary).uploader();
        verify(uploader).upload(any(), any(Map.class));
    }

    @Test
    void uploadFileEmptyThrowsException() {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "empty.png",
            "image/png",
            new byte[0]
        );

        // Act & Assert
        StorageException exception = assertThrows(StorageException.class, () -> 
            cloudinaryService.uploadFile(file, "questions")
        );
        assertThat(exception.getMessage()).isEqualTo(StorageConstants.EMPTY_FILE_MSG);
    }

    @Test
    void uploadFileNullThrowsException() {
        // Act & Assert
        StorageException exception = assertThrows(StorageException.class, () -> 
            cloudinaryService.uploadFile(null, "questions")
        );
        assertThat(exception.getMessage()).isEqualTo(StorageConstants.EMPTY_FILE_MSG);
    }

    @SuppressWarnings("unchecked")
    @Test
    void uploadFileThrowsIOException() throws IOException {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "test-image.png",
            "image/png",
            "test content".getBytes()
        );
        String folder = "questions";

        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(), any(Map.class))).thenThrow(new IOException("Connection failed"));

        // Act & Assert
        StorageException exception = assertThrows(StorageException.class, () -> 
            cloudinaryService.uploadFile(file, folder)
        );
        assertThat(exception.getMessage()).isEqualTo(StorageConstants.UPLOAD_FAILED_MSG);
        assertThat(exception.getCause()).isInstanceOf(IOException.class);
    }

    @SuppressWarnings("unchecked")
    @Test
    void deleteFileSuccess() throws IOException {
        // Arrange
        String storageKey = "questions/test";
        when(cloudinary.uploader()).thenReturn(uploader);

        // Act
        cloudinaryService.deleteFile(storageKey);

        // Assert
        verify(cloudinary).uploader();
        verify(uploader).destroy(eq(storageKey), any(Map.class));
    }

    @Test
    void deleteFileNullDoesNothing() {
        // Act
        cloudinaryService.deleteFile(null);
        cloudinaryService.deleteFile("");
        
        // Assert: No interactions with mock
    }

    @SuppressWarnings("unchecked")
    @Test
    void deleteFileThrowsIOException() throws IOException {
        // Arrange
        String storageKey = "questions/test";
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.destroy(eq(storageKey), any(Map.class))).thenThrow(new IOException("Connection failed"));

        // Act & Assert
        StorageException exception = assertThrows(StorageException.class, () -> 
            cloudinaryService.deleteFile(storageKey)
        );
        assertThat(exception.getMessage()).isEqualTo(StorageConstants.DELETE_FAILED_MSG);
        assertThat(exception.getCause()).isInstanceOf(IOException.class);
    }
}
