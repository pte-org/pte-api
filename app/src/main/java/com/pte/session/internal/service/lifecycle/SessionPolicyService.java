package com.pte.session.internal.service.lifecycle;

import com.pte.session.domain.ExamPolicy;
import com.pte.session.domain.ExamSession;
import com.pte.session.domain.ReplayPolicy;
import com.pte.session.domain.enums.ReplayPolicyType;
import com.pte.session.domain.enums.SessionStatus;
import com.pte.session.dto.response.ExamPolicyResponse;
import com.pte.session.internal.dto.request.PatchExamPolicyRequest;
import com.pte.session.internal.exception.InvalidPolicyPatchException;
import com.pte.session.internal.exception.PolicyLockedException;
import com.pte.session.internal.exception.SessionNotFoundException;
import com.pte.session.internal.mapper.SessionMapper;
import com.pte.session.internal.policy.SessionPolicyResolver;
import com.pte.session.internal.repository.ExamSessionRepository;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/** Edits policy only while the owned session remains scheduled. */
@Service
public class SessionPolicyService {

    private final ExamSessionRepository sessionRepository;
    private final SessionAccess access;

    public SessionPolicyService(ExamSessionRepository sessionRepository,
            SessionAccess access) {
        this.sessionRepository = sessionRepository;
        this.access = access;
    }

    @Transactional
    public ExamPolicyResponse patchPolicy(UUID publicId, PatchExamPolicyRequest request, CurrentUser caller) {
        ExamSession session = sessionRepository.findWithLockByPublicIdAndTenantId(publicId, access.requireTenant(caller))
                .orElseThrow(SessionNotFoundException::new);
        if (session.getStatus() != SessionStatus.SCHEDULED) {
            throw new PolicyLockedException();
        }

        ExamPolicy policy = session.getPolicy();
        if (request.replayPolicyType() != null) {
            if (request.replayPolicyType() == ReplayPolicyType.LIMITED && request.replayPolicyLimit() == null) {
                throw new InvalidPolicyPatchException();
            }
            policy.setReplayPolicy(ReplayPolicy.of(request.replayPolicyType(), request.replayPolicyLimit()));
        }
        if (request.deviceCheckRequired() != null) {
            policy.setDeviceCheckRequired(request.deviceCheckRequired());
        }
        if (request.proctorRequired() != null) {
            policy.setProctorRequired(request.proctorRequired());
        }
        if (request.answerIntegrityLevel() != null) {
            policy.setAnswerIntegrityLevel(request.answerIntegrityLevel());
        }
        if (request.lockdownMode() != null) {
            policy.setLockdownMode(SessionPolicyResolver.resolveForPolicyPatch(
                    policy.getLockdownMode(), request.lockdownMode()));
        }
        return SessionMapper.toPolicy(policy, session.getExamMode(), session.getExamMode() == null);
    }
}
