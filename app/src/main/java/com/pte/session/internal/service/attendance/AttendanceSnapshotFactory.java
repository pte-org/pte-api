package com.pte.session.internal.service.attendance;

import com.pte.session.domain.ExamRegistration;
import com.pte.session.domain.enums.AttendanceStatus;
import com.pte.session.domain.enums.IdentityVerificationStatus;
import com.pte.session.dto.response.AttendanceRowView;
import com.pte.session.internal.constant.AttendanceConstants;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** The attendance row a command or receipt returns: the registration's current state, or "not enrolled". */
@Component
class AttendanceSnapshotFactory {

    AttendanceRowView current(UUID studentPublicId, ExamRegistration registration) {
        if (registration == null) {
            return new AttendanceRowView(studentPublicId, null, null, null, AttendanceStatus.UNMARKED, 0L, null,
                    null, null, null, false, AttendanceConstants.BLOCK_NOT_ENROLLED, null, null,
                    IdentityVerificationStatus.UNVERIFIED.name(), null);
        }
        return new AttendanceRowView(studentPublicId, registration.getPublicId(), null, null,
                registration.getAttendanceStatus(), registration.getAttendanceVersion(),
                registration.getLastActorPublicId(), registration.getLastActorRole(),
                registration.getLastActionAt(), registration.getLastReasonOrNote(), true, null, null, null,
                registration.getIdentityStatus().name(), registration.getIdentityNote());
    }
}
