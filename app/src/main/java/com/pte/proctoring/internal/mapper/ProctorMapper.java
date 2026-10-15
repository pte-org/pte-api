package com.pte.proctoring.internal.mapper;

import com.pte.proctoring.domain.ProctorSession;
import com.pte.proctoring.domain.ViolationEvent;
import com.pte.proctoring.internal.dto.response.ProctorSessionResponse;
import com.pte.proctoring.internal.dto.response.ViolationEventResponse;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ProctorMapper {

    public ProctorSessionResponse toResponse(ProctorSession session) {
        return new ProctorSessionResponse(session.getPublicId(), session.getSessionPublicId(),
                session.getProctorPublicId(), session.getStatus(), session.getOpenedAt());
    }

    public ViolationEventResponse toResponse(ViolationEvent event) {
        UUID proctorPublicId = event.getProctorSession() != null ? event.getProctorSession().getProctorPublicId() : null;
        return new ViolationEventResponse(event.getPublicId(), event.getAttemptPublicId(), event.getViolationType(),
                event.getDetail(), event.getSequenceNo(), event.getHash(), event.getDetectedAt(), proctorPublicId);
    }
}
