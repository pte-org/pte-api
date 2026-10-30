package com.pte.proctoring.internal.mapper;

import com.pte.proctoring.domain.ExamIncident;
import com.pte.proctoring.domain.enums.ViolationType;
import com.pte.proctoring.internal.dto.response.ViolationEventResponse;
import org.springframework.stereotype.Component;

@Component
public class ProctorMapper {

    /** A proctor flag is an incident whose type is a {@link ViolationType} and whose reporter is the proctor. */
    public ViolationEventResponse toResponse(ExamIncident flag) {
        return new ViolationEventResponse(flag.getPublicId(), flag.getAttemptPublicId(),
                ViolationType.valueOf(flag.getType()), flag.getDescription(), flag.getDetectedAt(),
                flag.getReporterPublicId());
    }
}
