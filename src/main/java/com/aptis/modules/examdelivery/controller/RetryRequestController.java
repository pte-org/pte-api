package com.aptis.modules.examdelivery.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aptis.common.security.JwtPrincipal;
import com.aptis.modules.examdelivery.constant.ExamDeliveryApiConstants;
import com.aptis.modules.examdelivery.dto.RetryRequestResponse;
import com.aptis.modules.examdelivery.service.RetryRequestService;

@RestController
@RequestMapping(ExamDeliveryApiConstants.RETRY_REQUESTS)
public class RetryRequestController {

    private final RetryRequestService retryRequestService;

    public RetryRequestController(RetryRequestService retryRequestService) {
        this.retryRequestService = retryRequestService;
    }

    @PreAuthorize(ExamDeliveryApiConstants.AUTHORITY_HOST_OR_GRADER)
    @GetMapping(ExamDeliveryApiConstants.PATH_RETRY_PENDING)
    public ResponseEntity<List<RetryRequestResponse>> listPending(@AuthenticationPrincipal JwtPrincipal principal) {
        return ResponseEntity.ok(retryRequestService.listPending(principal));
    }

    @PreAuthorize(ExamDeliveryApiConstants.AUTHORITY_HOST_OR_GRADER)
    @PostMapping(ExamDeliveryApiConstants.PATH_RETRY_APPROVE)
    public ResponseEntity<RetryRequestResponse> approve(
            @PathVariable Long requestId,
            @AuthenticationPrincipal JwtPrincipal principal) {
        return ResponseEntity.ok(retryRequestService.approve(requestId, principal));
    }

    @PreAuthorize(ExamDeliveryApiConstants.AUTHORITY_HOST_OR_GRADER)
    @PostMapping(ExamDeliveryApiConstants.PATH_RETRY_REJECT)
    public ResponseEntity<RetryRequestResponse> reject(
            @PathVariable Long requestId,
            @AuthenticationPrincipal JwtPrincipal principal) {
        return ResponseEntity.ok(retryRequestService.reject(requestId, principal));
    }
}
