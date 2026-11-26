package com.pte.billing.internal.service;

import com.pte.billing.domain.ServicePlan;
import com.pte.billing.domain.enums.PlanStatus;
import com.pte.billing.domain.enums.PlanType;
import com.pte.billing.internal.dto.request.plan.PlanTransitionRequest;
import com.pte.billing.internal.repository.ServicePlanRepository;
import com.pte.shared.audit.AuditLogChangeWriter;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.audit.AuditStateChange;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** A plan reaches every tenant that buys it, so its edits keep before/after snapshots. */
@ExtendWith(MockitoExtension.class)
class PlanAuditTrailTest {

    private static final CurrentUser ADMIN = new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"));

    @Mock
    private ServicePlanRepository planRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private AuditLogChangeWriter changeWriter;

    private PlanService service;

    @BeforeEach
    void setUp() {
        service = new PlanService(planRepository, auditLogService, changeWriter, com.pte.TestClocks.fixed());
        lenient().when(planRepository.findReferencedPlanIds(any())).thenReturn(Set.of());
        lenient().when(planRepository.findOutstandingCodePlanIds(any(), any())).thenReturn(Set.of());
        lenient().when(planRepository.saveAndFlush(any(ServicePlan.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void activationRecordsTheStatusChangeAsBeforeAndAfter() {
        UUID publicId = UUID.randomUUID();
        when(planRepository.findByPublicIdForUpdate(publicId)).thenReturn(Optional.of(draft(publicId)));

        service.activate(publicId, new PlanTransitionRequest(0L), ADMIN);

        AuditStateChange change = recorded();
        assertThat(change.aggregateId()).isEqualTo(publicId.toString());
        assertThat(change.resourceName()).isEqualTo("Capacity 10");
        assertThat(((PlanAuditState) change.before()).status()).isEqualTo("DRAFT");
        assertThat(((PlanAuditState) change.after()).status()).isEqualTo("ACTIVE");
        verifyNoInteractions(auditLogService);
    }

    @Test
    void deletingADraftKeepsTheLastStateAndNoAfterState() {
        UUID publicId = UUID.randomUUID();
        when(planRepository.findByPublicIdForUpdate(publicId)).thenReturn(Optional.of(draft(publicId)));

        service.deleteDraft(publicId, ADMIN);

        AuditStateChange change = recorded();
        assertThat(((PlanAuditState) change.before()).price()).isEqualByComparingTo("150000");
        assertThat(change.after()).isNull();
    }

    private AuditStateChange recorded() {
        ArgumentCaptor<AuditStateChange> captor = ArgumentCaptor.forClass(AuditStateChange.class);
        verify(changeWriter).recordState(eq(ADMIN), captor.capture());
        return captor.getValue();
    }

    private static ServicePlan draft(UUID publicId) {
        ServicePlan plan = new ServicePlan();
        plan.setPublicId(publicId);
        plan.setName("Capacity 10");
        plan.setType(PlanType.STUDENT_CAPACITY);
        plan.setPrice(new BigDecimal("150000"));
        plan.setCurrency("VND");
        plan.setExtraStudentSlots(10);
        plan.setStatus(PlanStatus.DRAFT);
        return plan;
    }
}
