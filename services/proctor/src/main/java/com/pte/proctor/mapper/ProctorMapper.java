package com.pte.proctor.mapper;

import com.pte.proctor.domain.ProctorSession;
import com.pte.proctor.domain.ViolationEvent;
import com.pte.proctor.dto.response.ProctorSessionResponse;
import com.pte.proctor.dto.response.ViolationEventResponse;
import org.springframework.stereotype.Component;

@Component
public class ProctorMapper {

    public ProctorSessionResponse toResponse(ProctorSession session) {
        return new ProctorSessionResponse(session.getPublicId(), session.getSessionPublicId(),
                session.getProctorPublicId(), session.getStatus(), session.getOpenedAt());
    }

    public ViolationEventResponse toResponse(ViolationEvent event) {
        return new ViolationEventResponse(event.getPublicId(), event.getAttemptPublicId(), event.getViolationType(),
                event.getDetail(), event.getSequenceNo(), event.getHash(), event.getDetectedAt());
    }
}
