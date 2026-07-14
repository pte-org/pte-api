package com.aptis.modules.examdelivery.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.aptis.modules.examdelivery.constant.ExamDeliveryApiConstants;
import com.aptis.modules.examdelivery.constant.ExamDeliveryConstants;
import com.aptis.modules.examdelivery.dto.SpeakingUploadResponse;
import com.aptis.modules.examdelivery.dto.SubmitAnswerRequest;
import com.aptis.modules.examdelivery.service.ExamAttemptService;
import com.aptis.modules.storage.interfaces.StorageService;
import com.aptis.modules.storage.dto.response.UploadResponse;

import java.util.Map;

@RestController
@RequestMapping(ExamDeliveryApiConstants.EXAM_ATTEMPTS)
public class ExamAttemptController {

    private final ExamAttemptService examAttemptService;
    private final StorageService storageService;

    public ExamAttemptController(
            ExamAttemptService examAttemptService,
            StorageService storageService) {
        this.examAttemptService = examAttemptService;
        this.storageService = storageService;
    }

    @PostMapping("/{attemptId}/speaking/upload")
    public ResponseEntity<SpeakingUploadResponse> uploadSpeakingRecording(
            @PathVariable Long attemptId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("questionId") Long questionId) {
        
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

    @PostMapping("/{attemptId}/answers")
    public ResponseEntity<Map<String, String>> submitAnswer(
            @PathVariable Long attemptId,
            @RequestBody SubmitAnswerRequest request) {
        
        examAttemptService.submitAnswer(attemptId, request);
        
        return ResponseEntity.ok(Map.of(
                ExamDeliveryConstants.RESP_STATUS_KEY, ExamDeliveryConstants.RESP_STATUS_SUCCESS,
                ExamDeliveryConstants.RESP_MESSAGE_KEY, ExamDeliveryConstants.RESP_MESSAGE_SUCCESS
        ));
    }
}
