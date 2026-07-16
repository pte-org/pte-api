package com.aptis.modules.examdelivery.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.aptis.common.security.JwtPrincipal;
import com.aptis.modules.examdelivery.constant.ExamDeliveryApiConstants;
import com.aptis.modules.examdelivery.constant.ExamDeliveryConstants;
import com.aptis.modules.examdelivery.dto.ExamAttemptResponse;
import com.aptis.modules.examdelivery.dto.HeartbeatResponse;
import com.aptis.modules.examdelivery.dto.RedactedQuestionResponse;
import com.aptis.modules.examdelivery.dto.RetryRequestResponse;
import com.aptis.modules.examdelivery.dto.SectionSummaryResponse;
import com.aptis.modules.examdelivery.dto.SpeakingUploadResponse;
import com.aptis.modules.examdelivery.dto.SubmitAnswerRequest;
import com.aptis.modules.examdelivery.dto.SubmitExamRequest;
import com.aptis.modules.examdelivery.service.ExamAttemptAccessGuard;
import com.aptis.modules.examdelivery.service.ExamAttemptService;
import com.aptis.modules.examdelivery.service.ExamContentDeliveryService;
import com.aptis.modules.examdelivery.service.RetryRequestService;
import com.aptis.modules.storage.interfaces.StorageService;
import com.aptis.modules.storage.dto.response.UploadResponse;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(ExamDeliveryApiConstants.EXAM_ATTEMPTS)
public class ExamAttemptController {

    private final ExamAttemptService examAttemptService;
    private final ExamContentDeliveryService contentDeliveryService;
    private final ExamAttemptAccessGuard accessGuard;
    private final StorageService storageService;
    private final RetryRequestService retryRequestService;

    public ExamAttemptController(
            ExamAttemptService examAttemptService,
            ExamContentDeliveryService contentDeliveryService,
            ExamAttemptAccessGuard accessGuard,
            StorageService storageService,
            RetryRequestService retryRequestService) {
        this.examAttemptService = examAttemptService;
        this.contentDeliveryService = contentDeliveryService;
        this.accessGuard = accessGuard;
        this.storageService = storageService;
        this.retryRequestService = retryRequestService;
    }

    @PreAuthorize(ExamDeliveryApiConstants.AUTHORITY_STUDENT)
    @GetMapping(ExamDeliveryApiConstants.PATH_SECTIONS)
    public ResponseEntity<List<SectionSummaryResponse>> getSections(
            @PathVariable Long attemptId,
            @AuthenticationPrincipal JwtPrincipal principal) {

        return ResponseEntity.ok(contentDeliveryService.getSections(attemptId, principal));
    }

    @PreAuthorize(ExamDeliveryApiConstants.AUTHORITY_STUDENT)
    @GetMapping(ExamDeliveryApiConstants.PATH_SECTION_QUESTIONS)
    public ResponseEntity<List<RedactedQuestionResponse>> getSectionQuestions(
            @PathVariable Long attemptId,
            @PathVariable String skill,
            @AuthenticationPrincipal JwtPrincipal principal) {

        return ResponseEntity.ok(contentDeliveryService.getSectionQuestions(attemptId, skill, principal));
    }

    @PreAuthorize(ExamDeliveryApiConstants.AUTHORITY_STUDENT)
    @PostMapping("/{attemptId}/speaking/upload")
    public ResponseEntity<SpeakingUploadResponse> uploadSpeakingRecording(
            @PathVariable Long attemptId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("questionId") Long questionId,
            @AuthenticationPrincipal JwtPrincipal principal) {

        // Ownership + availability check before touching storage.
        accessGuard.checkAvailabilityWindow(accessGuard.loadOwnedAttempt(attemptId, principal).getExam());

        // Target folder structure: aptis/speaking/attempts/{attemptId}/
        String folder = ExamDeliveryConstants.FOLDER_ATTEMPTS + attemptId;
        UploadResponse uploadResponse = storageService.uploadFile(file, folder);

        SpeakingUploadResponse response = new SpeakingUploadResponse(
                questionId,
                uploadResponse.cdnUrl(),
                file.getSize(),
                file.getContentType()
        );
        return ResponseEntity.ok(response);
    }

    @PreAuthorize(ExamDeliveryApiConstants.AUTHORITY_STUDENT)
    @PostMapping("/{attemptId}/answers")
    public ResponseEntity<Map<String, String>> submitAnswer(
            @PathVariable Long attemptId,
            @RequestBody SubmitAnswerRequest request,
            @AuthenticationPrincipal JwtPrincipal principal) {

        examAttemptService.submitAnswer(attemptId, request, principal);

        return ResponseEntity.ok(Map.of(
                ExamDeliveryConstants.RESP_STATUS_KEY, ExamDeliveryConstants.RESP_STATUS_SUCCESS,
                ExamDeliveryConstants.RESP_MESSAGE_KEY, ExamDeliveryConstants.RESP_MESSAGE_SUCCESS
        ));
    }

    @PreAuthorize(ExamDeliveryApiConstants.AUTHORITY_STUDENT)
    @PostMapping(ExamDeliveryApiConstants.PATH_HEARTBEAT)
    public ResponseEntity<HeartbeatResponse> heartbeat(
            @PathVariable Long attemptId,
            @AuthenticationPrincipal JwtPrincipal principal) {

        return ResponseEntity.ok(examAttemptService.recordHeartbeat(attemptId, principal));
    }

    @PreAuthorize(ExamDeliveryApiConstants.AUTHORITY_STUDENT)
    @PostMapping(ExamDeliveryApiConstants.PATH_SECTION_SWITCH)
    public ResponseEntity<ExamAttemptResponse> switchSection(
            @PathVariable Long attemptId,
            @AuthenticationPrincipal JwtPrincipal principal) {

        return ResponseEntity.ok(examAttemptService.switchSection(attemptId, principal));
    }

    @PreAuthorize(ExamDeliveryApiConstants.AUTHORITY_STUDENT)
    @PostMapping(ExamDeliveryApiConstants.PATH_SUBMIT)
    public ResponseEntity<ExamAttemptResponse> submitAttempt(
            @PathVariable Long attemptId,
            @AuthenticationPrincipal JwtPrincipal principal) {

        return ResponseEntity.ok(examAttemptService.submitAttempt(new SubmitExamRequest(attemptId), principal));
    }

    @PreAuthorize(ExamDeliveryApiConstants.AUTHORITY_STUDENT)
    @PostMapping(ExamDeliveryApiConstants.PATH_RETRY_REQUEST)
    public ResponseEntity<RetryRequestResponse> requestRetry(
            @PathVariable Long attemptId,
            @AuthenticationPrincipal JwtPrincipal principal) {

        return ResponseEntity.ok(retryRequestService.requestRetry(attemptId, principal));
    }
}
