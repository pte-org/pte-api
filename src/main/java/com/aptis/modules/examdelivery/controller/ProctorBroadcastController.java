package com.aptis.modules.examdelivery.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aptis.common.security.JwtPrincipal;
import com.aptis.modules.examdelivery.service.ProctorBroadcastService;
import com.aptis.modules.proctor.constant.ProctoringApiConstants;
import com.aptis.modules.proctor.dto.BroadcastRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping(ProctoringApiConstants.PROCTOR_SESSIONS)
public class ProctorBroadcastController {

    private final ProctorBroadcastService broadcastService;

    public ProctorBroadcastController(ProctorBroadcastService broadcastService) {
        this.broadcastService = broadcastService;
    }

    @PreAuthorize(ProctoringApiConstants.AUTHORITY_PROCTOR)
    @PostMapping(ProctoringApiConstants.PATH_BROADCAST)
    public ResponseEntity<Map<String, Object>> broadcast(
            @PathVariable Long examId,
            @Valid @RequestBody BroadcastRequest request,
            @AuthenticationPrincipal JwtPrincipal principal) {

        int recipientCount = broadcastService.broadcast(examId, request.message(), principal);
        return ResponseEntity.ok(Map.of("examId", examId, "recipientCount", recipientCount));
    }
}
