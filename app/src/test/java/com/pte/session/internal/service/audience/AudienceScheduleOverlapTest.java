package com.pte.session.internal.service.audience;

import com.pte.enrollment.EnrollmentModuleService;
import com.pte.identity.IdentityService;
import com.pte.session.domain.ExamRegistration;
import com.pte.session.domain.ExamSession;
import com.pte.session.domain.ExamSessionAudienceSource;
import com.pte.session.domain.enums.AudienceMemberStatus;
import com.pte.session.domain.enums.AudienceSourceType;
import com.pte.session.domain.enums.ReusePolicy;
import com.pte.session.domain.enums.SessionStatus;
import com.pte.session.internal.repository.ExamRegistrationRepository;
import com.pte.session.internal.repository.ExamSessionAudienceSourceRepository;
import com.pte.shared.StartedAttemptLookup;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AudienceScheduleOverlapTest {
    @ParameterizedTest
    @EnumSource(SessionStatus.class)
    void onlyScheduledAndOpenSessionsExcludeAnAudienceMember(SessionStatus status) {
        var sources = mock(ExamSessionAudienceSourceRepository.class);
        var registrations = mock(ExamRegistrationRepository.class);
        var resolver = new AudienceResolver(sources, registrations, mock(EnrollmentModuleService.class),
                mock(IdentityService.class), mock(StartedAttemptLookup.class));
        UUID tenant = UUID.randomUUID();
        UUID student = UUID.randomUUID();
        ExamSession exam = new ExamSession();
        exam.setId(1L);
        exam.setTenantId(tenant);
        exam.setPublicId(UUID.randomUUID());
        exam.setCapacity(10);
        exam.setReusePolicy(ReusePolicy.BLOCK_ON_SCHEDULE_OVERLAP);
        exam.setOpensAt(Instant.parse("2026-10-17T09:00:00Z"));
        exam.setClosesAt(Instant.parse("2026-10-17T11:00:00Z"));
        ExamSession prior = new ExamSession();
        prior.setPublicId(UUID.randomUUID());
        prior.setStatus(status);
        prior.setName("Earlier exam");
        prior.setOpensAt(Instant.parse("2026-10-17T08:00:00Z"));
        prior.setClosesAt(Instant.parse("2026-10-17T10:00:00Z"));
        ExamRegistration enrolled = new ExamRegistration();
        enrolled.setStudentPublicId(student);
        enrolled.setSession(prior);
        ExamSessionAudienceSource source = new ExamSessionAudienceSource();
        source.setSourceType(AudienceSourceType.STUDENT);
        source.setSourcePublicId(student);
        when(sources.findBySessionIdOrderByCreatedAtAsc(1L)).thenReturn(List.of(source));
        when(registrations.findEnrollmentsOfStudents(tenant, List.of(student))).thenReturn(List.of(enrolled));
        var audience = resolver.resolve(exam, false);
        boolean blocks = status == SessionStatus.SCHEDULED || status == SessionStatus.OPEN;
        assertThat(audience.blockedCount()).isEqualTo(blocks ? 1 : 0);
        assertThat(audience.members().getFirst().status())
                .isEqualTo(blocks ? AudienceMemberStatus.EXCLUDED : AudienceMemberStatus.ELIGIBLE);
    }
}
