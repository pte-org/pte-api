package com.pte.scoring.internal.service;

import com.pte.media.MediaService;
import com.pte.media.dto.response.PresignedDownloadResponse;
import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.internal.dto.response.AnswerPayloadKind;
import com.pte.scoring.internal.dto.response.AnswerReviewDetailResponse;
import com.pte.scoring.internal.dto.response.DecodedAnswerPayload;
import com.pte.scoring.internal.exception.AnswerNotFoundException;
import com.pte.scoring.internal.repository.ScoringAnswerRepository;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScoringReviewServiceTest {

    @Mock
    private ScoringAnswerRepository scoringAnswerRepository;
    @Mock
    private AnswerPayloadDecoder answerPayloadDecoder;
    @Mock
    private MediaService mediaService;

    private ScoringReviewService service;
    private CurrentUser caller;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        service = new ScoringReviewService(scoringAnswerRepository, answerPayloadDecoder, mediaService);
        tenantId = UUID.randomUUID();
        caller = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_AUTHOR"));
    }

    @Test
    void getAnswerForReview_answerNotFound_throwsNotFoundException() {
        UUID answerPublicId = UUID.randomUUID();
        when(scoringAnswerRepository.findByAnswerPublicId(answerPublicId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAnswerForReview(answerPublicId, caller))
                .isInstanceOf(AnswerNotFoundException.class);
    }

    @Test
    void getAnswerForReview_wrongTenant_throwsNotFoundException() {
        UUID answerPublicId = UUID.randomUUID();
        UUID wrongTenantId = UUID.randomUUID();

        ScoringAnswer answer = new ScoringAnswer();
        answer.setAnswerPublicId(answerPublicId);
        answer.setTenantId(wrongTenantId);
        answer.setStatus(ScoringAnswerStatus.SCORED);
        answer.setRawScore(85);
        answer.setCreatedAt(Instant.now());

        when(scoringAnswerRepository.findByAnswerPublicId(answerPublicId))
                .thenReturn(Optional.of(answer));

        assertThatThrownBy(() -> service.getAnswerForReview(answerPublicId, caller))
                .isInstanceOf(AnswerNotFoundException.class);
    }

    @Test
    void getAnswerForReview_textPayload_returnsAnswerWithoutMediaProcessing() {
        UUID answerPublicId = UUID.randomUUID();

        ScoringAnswer answer = new ScoringAnswer();
        answer.setAnswerPublicId(answerPublicId);
        answer.setAttemptPublicId(UUID.randomUUID());
        answer.setSessionPublicId(UUID.randomUUID());
        answer.setTaskType("SUMMARIZE_SPOKEN_TEXT");
        answer.setTenantId(tenantId);
        answer.setStatus(ScoringAnswerStatus.SCORED);
        answer.setRawScore(80);
        answer.setCreatedAt(Instant.now());

        DecodedAnswerPayload decoded = new DecodedAnswerPayload(
                AnswerPayloadKind.TEXT, "sample text", null, null, null, null, null);

        when(scoringAnswerRepository.findByAnswerPublicId(answerPublicId))
                .thenReturn(Optional.of(answer));
        when(answerPayloadDecoder.decode(answer)).thenReturn(decoded);

        AnswerReviewDetailResponse response = service.getAnswerForReview(answerPublicId, caller);

        assertThat(response.answerPublicId()).isEqualTo(answerPublicId);
        assertThat(response.payload().kind()).isEqualTo(AnswerPayloadKind.TEXT);
        verify(mediaService, never()).presignGet(any(), any(Long.class), any());
    }

    @Test
    void getAnswerForReview_audioPayload_presignsMediaUrl() {
        UUID answerPublicId = UUID.randomUUID();
        UUID mediaPublicId = UUID.randomUUID();
        String presignedUrl = "https://presigned.url";

        ScoringAnswer answer = new ScoringAnswer();
        answer.setAnswerPublicId(answerPublicId);
        answer.setAttemptPublicId(UUID.randomUUID());
        answer.setSessionPublicId(UUID.randomUUID());
        answer.setTaskType("READ_ALOUD");
        answer.setTenantId(tenantId);
        answer.setStatus(ScoringAnswerStatus.SCORED);
        answer.setRawScore(75);
        answer.setCreatedAt(Instant.now());

        DecodedAnswerPayload decodedWithoutUrl = new DecodedAnswerPayload(
                AnswerPayloadKind.AUDIO, null, mediaPublicId, null, null, null, null);

        when(scoringAnswerRepository.findByAnswerPublicId(answerPublicId))
                .thenReturn(Optional.of(answer));
        when(answerPayloadDecoder.decode(answer)).thenReturn(decodedWithoutUrl);
        when(mediaService.presignGet(mediaPublicId, 3600L, tenantId))
                .thenReturn(new PresignedDownloadResponse(presignedUrl, 3600L, null));

        AnswerReviewDetailResponse response = service.getAnswerForReview(answerPublicId, caller);

        assertThat(response.payload().kind()).isEqualTo(AnswerPayloadKind.AUDIO);
        assertThat(response.payload().mediaUrl()).isEqualTo(presignedUrl);
    }

    @Test
    void getAnswerForReview_mediaPresignFails_returnsAnswerWithoutUrl() {
        UUID answerPublicId = UUID.randomUUID();
        UUID mediaPublicId = UUID.randomUUID();

        ScoringAnswer answer = new ScoringAnswer();
        answer.setAnswerPublicId(answerPublicId);
        answer.setAttemptPublicId(UUID.randomUUID());
        answer.setSessionPublicId(UUID.randomUUID());
        answer.setTaskType("READ_ALOUD");
        answer.setTenantId(tenantId);
        answer.setStatus(ScoringAnswerStatus.SCORED);
        answer.setRawScore(75);
        answer.setCreatedAt(Instant.now());

        DecodedAnswerPayload decodedWithoutUrl = new DecodedAnswerPayload(
                AnswerPayloadKind.AUDIO, null, mediaPublicId, null, null, null, null);

        when(scoringAnswerRepository.findByAnswerPublicId(answerPublicId))
                .thenReturn(Optional.of(answer));
        when(answerPayloadDecoder.decode(answer)).thenReturn(decodedWithoutUrl);
        when(mediaService.presignGet(mediaPublicId, 3600L, tenantId))
                .thenThrow(new RuntimeException("Media service unavailable"));

        AnswerReviewDetailResponse response = service.getAnswerForReview(answerPublicId, caller);

        // Should return answer without propagating the exception
        assertThat(response.payload().kind()).isEqualTo(AnswerPayloadKind.AUDIO);
        assertThat(response.payload().mediaUrl()).isNull();
    }

    @Test
    void submitTeacherScore_recordsScoreAndSaves() {
        UUID answerPublicId = UUID.randomUUID();
        int teacherScore = 88;

        ScoringAnswer answer = new ScoringAnswer();
        answer.setAnswerPublicId(answerPublicId);
        answer.setAttemptPublicId(UUID.randomUUID());
        answer.setSessionPublicId(UUID.randomUUID());
        answer.setTaskType("SUMMARIZE_WRITTEN_TEXT");
        answer.setTenantId(tenantId);
        answer.setStatus(ScoringAnswerStatus.SCORED);
        answer.setRawScore(90);
        answer.setTeacherScore(null);
        answer.setTeacherScoredAt(null);
        answer.setCreatedAt(Instant.now());

        when(scoringAnswerRepository.findByAnswerPublicId(answerPublicId))
                .thenReturn(Optional.of(answer));
        when(scoringAnswerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.submitTeacherScore(answerPublicId, teacherScore, caller);

        assertThat(answer.getTeacherScore()).isEqualTo(teacherScore);
        assertThat(answer.getTeacherScoredAt()).isNotNull();
        verify(scoringAnswerRepository).save(answer);
    }

    @Test
    void submitTeacherScore_independentOfRawScore() {
        UUID answerPublicId = UUID.randomUUID();
        int teacherScore = 50;

        ScoringAnswer answer = new ScoringAnswer();
        answer.setAnswerPublicId(answerPublicId);
        answer.setAttemptPublicId(UUID.randomUUID());
        answer.setSessionPublicId(UUID.randomUUID());
        answer.setTaskType("SUMMARIZE_WRITTEN_TEXT");
        answer.setTenantId(tenantId);
        answer.setStatus(ScoringAnswerStatus.SCORED);
        answer.setRawScore(95);
        answer.setCreatedAt(Instant.now());

        when(scoringAnswerRepository.findByAnswerPublicId(answerPublicId))
                .thenReturn(Optional.of(answer));
        when(scoringAnswerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.submitTeacherScore(answerPublicId, teacherScore, caller);

        // Should not modify rawScore, only set teacherScore
        assertThat(answer.getRawScore()).isEqualTo(95);
        assertThat(answer.getTeacherScore()).isEqualTo(50);
    }
}
