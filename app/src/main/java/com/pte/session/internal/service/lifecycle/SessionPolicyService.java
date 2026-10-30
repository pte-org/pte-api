package com.pte.session.internal.service.lifecycle;

import com.pte.session.domain.ExamPolicy;
import com.pte.session.domain.ExamSession;
import com.pte.session.domain.ReplayPolicy;
import com.pte.session.domain.enums.ReplayPolicyType;
import com.pte.session.domain.enums.SessionStatus;
import com.pte.session.dto.response.ExamPolicyResponse;
import com.pte.session.internal.dto.request.PatchExamPolicyRequest;
import com.pte.session.internal.exception.draft.InvalidPolicyPatchException;
import com.pte.session.internal.exception.draft.PolicyLockedException;
import com.pte.session.internal.exception.SessionNotFoundException;
import com.pte.session.internal.mapper.SessionMapper;
import com.pte.session.internal.policy.SessionPolicyResolver;
import com.pte.session.internal.repository.ExamSessionRepository;
import com.pte.shared.security.CurrentUser;
import com.pte.tenancy.TenancyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.UUID;

/** Edits policy only while the owned session remains scheduled. */
@Service
public class SessionPolicyService {

    private final ExamSessionRepository sessionRepository;
    private final SessionAccess access;
    private final Clock clock;
    private final TenancyService tenancyService;

    public SessionPolicyService(ExamSessionRepository sessionRepository,
            SessionAccess access, Clock clock) {
        this(sessionRepository, access, clock, null);
    }

    @Autowired
    public SessionPolicyService(ExamSessionRepository sessionRepository,
            SessionAccess access, Clock clock, TenancyService tenancyService) {
        this.sessionRepository = sessionRepository;
        this.access = access;
        this.clock = clock;
        this.tenancyService = tenancyService;
    }

    @Transactional
    public ExamPolicyResponse patchPolicy(UUID publicId, PatchExamPolicyRequest request, CurrentUser caller) {
        UUID tenantId = access.requireTenant(caller);
        assertTenantActive(tenantId);
        ExamSession session = sessionRepository.findWithLockByPublicIdAndTenantId(publicId, tenantId)
                .orElseThrow(SessionNotFoundException::new);
        if (session.getStatus() != SessionStatus.SCHEDULED
                || !session.getOpensAt().isAfter(clock.instant())) {
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

    private void assertTenantActive(UUID tenantId) {
        if (tenancyService != null) {
            tenancyService.assertTenantActive(tenantId);
        }
    }
}
