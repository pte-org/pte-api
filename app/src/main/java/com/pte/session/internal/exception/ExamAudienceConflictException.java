package com.pte.session.internal.exception;

import com.pte.session.dto.response.SlotConflict;
import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

import java.util.List;

public class ExamAudienceConflictException extends DomainException {

    public ExamAudienceConflictException(List<SlotConflict> conflicts) {
        super(HttpStatus.CONFLICT, SessionConstants.EXAM_AUDIENCE_CONFLICT,
                List.copyOf(conflicts), SessionConstants.EXAM_AUDIENCE_CONFLICT_FRIENDLY);
    }
}
