package com.aptis.modules.examdelivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import com.aptis.common.exception.ApiException;
import com.aptis.common.exception.ErrorCode;
import com.aptis.modules.examdelivery.controller.ExamAttemptController;
import com.aptis.modules.examdelivery.dto.SpeakingUploadResponse;
import com.aptis.modules.examdelivery.dto.SubmitAnswerRequest;
import com.aptis.modules.examdelivery.domain.ExamAttempt;
import com.aptis.modules.examdelivery.domain.AttemptAnswer;
import com.aptis.modules.examdelivery.repository.ExamAttemptRepository;
import com.aptis.modules.examdelivery.repository.AttemptAnswerRepository;
import com.aptis.modules.examdelivery.service.ExamAttemptService;
import com.aptis.modules.storage.interfaces.StorageService;
import com.aptis.modules.storage.dto.response.UploadResponse;

@ExtendWith(MockitoExtension.class)
class ExamAttemptControllerAndServiceTest {

    @Mock
    private ExamAttemptRepository examAttemptRepository;

    @Mock
    private AttemptAnswerRepository attemptAnswerRepository;

    @Mock
    private StorageService storageService;

    private ExamAttemptService examAttemptService;
    private ExamAttemptController examAttemptController;

    @BeforeEach
    void setUp() {
        examAttemptService = new ExamAttemptService(examAttemptRepository, attemptAnswerRepository);
        examAttemptController = new ExamAttemptController(examAttemptService, storageService);
    }

    @Test
    void submitAnswerSuccessNew() {
        // Arrange
        Long attemptId = 1L;
        SubmitAnswerRequest request = new SubmitAnswerRequest(10L, "AUDIO_RECORD", "https://res.cloudinary.com/test.mp3");

        ExamAttempt attempt = mock(ExamAttempt.class);
        when(attempt.getIsSubmitted()).thenReturn(false);
        when(examAttemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));
        when(attemptAnswerRepository.findByAttemptIdAndQuestionId(attemptId, 10L)).thenReturn(Optional.empty());

        // Act
        examAttemptService.submitAnswer(attemptId, request);

        // Assert
        verify(attemptAnswerRepository).save(any(AttemptAnswer.class));
    }

    @Test
    void submitAnswerSuccessUpdate() {
        // Arrange
        Long attemptId = 1L;
        SubmitAnswerRequest request = new SubmitAnswerRequest(10L, "AUDIO_RECORD", "https://res.cloudinary.com/new.mp3");

        ExamAttempt attempt = mock(ExamAttempt.class);
        when(attempt.getIsSubmitted()).thenReturn(false);
        when(examAttemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));

        AttemptAnswer existingAnswer = mock(AttemptAnswer.class);
        when(attemptAnswerRepository.findByAttemptIdAndQuestionId(attemptId, 10L)).thenReturn(Optional.of(existingAnswer));

        // Act
        examAttemptService.submitAnswer(attemptId, request);

        // Assert
        verify(existingAnswer).setQuestionType("AUDIO_RECORD");
        verify(existingAnswer).setContent("https://res.cloudinary.com/new.mp3");
        verify(attemptAnswerRepository).save(existingAnswer);
    }

    @Test
    void submitAnswerThrowsWhenAttemptSubmitted() {
        // Arrange
        Long attemptId = 1L;
        SubmitAnswerRequest request = new SubmitAnswerRequest(10L, "AUDIO_RECORD", "https://res.cloudinary.com/test.mp3");

        ExamAttempt attempt = mock(ExamAttempt.class);
        when(attempt.getIsSubmitted()).thenReturn(true);
        when(examAttemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));

        // Act & Assert
        ApiException exception = assertThrows(ApiException.class, () ->
                examAttemptService.submitAnswer(attemptId, request)
        );
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_CONFLICT);
        verify(attemptAnswerRepository, never()).save(any());
    }

    @Test
    void uploadSpeakingRecordingSuccess() {
        // Arrange
        Long attemptId = 1L;
        Long questionId = 10L;
        MockMultipartFile file = new MockMultipartFile("file", "test.mp3", "audio/mpeg", "test content".getBytes());
        UploadResponse uploadResponse = new UploadResponse("key", "https://res.cloudinary.com/test.mp3", "test.mp3", 12L, "audio/mpeg");

        when(storageService.uploadFile(eq(file), eq("attempts/1"))).thenReturn(uploadResponse);

        // Act
        ResponseEntity<SpeakingUploadResponse> responseEntity = examAttemptController.uploadSpeakingRecording(attemptId, file, questionId);

        // Assert
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        SpeakingUploadResponse body = responseEntity.getBody();
        assertThat(body).isNotNull();
        assertThat(body.questionId()).isEqualTo(questionId);
        assertThat(body.audioUrl()).isEqualTo("https://res.cloudinary.com/test.mp3");
        assertThat(body.fileSize()).isEqualTo(file.getSize());
        assertThat(body.mimeType()).isEqualTo("audio/mpeg");
    }

    @Test
    void submitAnswerControllerSuccess() {
        // Arrange
        Long attemptId = 1L;
        SubmitAnswerRequest request = new SubmitAnswerRequest(10L, "AUDIO_RECORD", "https://res.cloudinary.com/test.mp3");

        ExamAttempt attempt = mock(ExamAttempt.class);
        when(attempt.getIsSubmitted()).thenReturn(false);
        when(examAttemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));
        when(attemptAnswerRepository.findByAttemptIdAndQuestionId(attemptId, 10L)).thenReturn(Optional.empty());

        // Act
        ResponseEntity<Map<String, String>> responseEntity = examAttemptController.submitAnswer(attemptId, request);

        // Assert
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(responseEntity.getBody().get("status")).isEqualTo("SUCCESS");
        verify(attemptAnswerRepository).save(any(AttemptAnswer.class));
    }
}
