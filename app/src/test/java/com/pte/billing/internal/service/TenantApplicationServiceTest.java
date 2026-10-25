package com.pte.billing.internal.service;

import com.pte.billing.domain.enums.TenantApplicationStatus;
import com.pte.billing.TenantApplicationApprovedEvent;
import com.pte.billing.internal.dto.request.application.RejectApplicationRequest;
import com.pte.billing.internal.dto.request.application.SubmitApplicationRequest;
import com.pte.billing.internal.dto.response.TenantApplicationResponse;
import com.pte.billing.internal.constant.BillingConstants;
import com.pte.billing.internal.exception.application.ApplicationNotPendingException;
import com.pte.billing.internal.exception.license.RequestedCodeAlreadyUsedException;
import com.pte.billing.internal.exception.application.RequestedNameAlreadyUsedException;
import com.pte.billing.internal.exception.application.RequestedTaxCodeAlreadyUsedException;
import com.pte.billing.internal.exception.application.TenantApplicationNotFoundException;
import com.pte.billing.internal.exception.application.TenantApplicationValidationException;
import com.pte.billing.internal.mapper.TenantApplicationMapper;
import com.pte.identity.domain.HostAdminCreated;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.IdentityService;
import com.pte.shared.security.CurrentUser;
import com.pte.tenancy.TenancyService;
import com.pte.tenancy.domain.Organization;
import com.pte.tenancy.domain.enums.OrganizationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantApplicationServiceTest {

    @Mock
    private TenancyService tenancyService;

    @Mock
    private IdentityService identityService;

    @Mock
    private PlatformSettingService platformSettingService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private TenantApplicationService service;

    @BeforeEach
    void setUp() {
        service = new TenantApplicationService(tenancyService, identityService,
                platformSettingService, eventPublisher);
    }

    private SubmitApplicationRequest submitRequest(String code) {
        return new SubmitApplicationRequest("Acme School", "SCHOOL", code, "contact@acme.example", null,
                "0123456789");
    }

    @Test
    void submit_savesApplicationAsPending() {
        Organization saved = pendingApplication(UUID.randomUUID(), "acme");
        when(tenancyService.existsByName("Acme School")).thenReturn(false);
        when(tenancyService.existsByCode("acme")).thenReturn(false);
        when(tenancyService.submitApplication("Acme School", "SCHOOL", "acme", "0123456789",
                "contact@acme.example", null)).thenReturn(saved);

        service.submit(submitRequest("acme"));

        verify(tenancyService).submitApplication("Acme School", "SCHOOL", "acme", "0123456789",
                "contact@acme.example", null);
        verify(eventPublisher).publishEvent(any(Object.class));
    }

    @Test
    void submit_codeAlreadyATenant_throwsWithoutSaving() {
        when(tenancyService.existsByName("Acme School")).thenReturn(false);
        when(tenancyService.existsByCode("acme")).thenReturn(true);

        assertThatThrownBy(() -> service.submit(submitRequest("acme")))
                .isInstanceOf(RequestedCodeAlreadyUsedException.class);
        verify(tenancyService, never()).submitApplication(any(), any(), any(), any(), any(), any());
    }

    // A pending application is an organization row now, so "held by another pending application" is the same
    // existsByCode check; the PENDING case itself is covered by OrganizationRepositoryTest.
    @Test
    void submit_codeHeldByAnotherPendingApplication_throwsWithoutSaving() {
        when(tenancyService.existsByName("Acme School")).thenReturn(false);
        when(tenancyService.existsByCode("acme")).thenReturn(true);

        assertThatThrownBy(() -> service.submit(submitRequest("acme")))
                .isInstanceOf(RequestedCodeAlreadyUsedException.class);
        verify(tenancyService, never()).submitApplication(any(), any(), any(), any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void submit_nameAlreadyATenant_throwsWithoutSaving() {
        when(tenancyService.existsByName("Acme School")).thenReturn(true);

        assertThatThrownBy(() -> service.submit(submitRequest("acme-new")))
                .isInstanceOf(RequestedNameAlreadyUsedException.class);
        verify(tenancyService, never()).submitApplication(any(), any(), any(), any(), any(), any());
    }

    @Test
    void submit_nameHeldByAnotherPendingApplication_throwsWithoutSaving() {
        when(tenancyService.existsByName("Acme School")).thenReturn(true);

        assertThatThrownBy(() -> service.submit(submitRequest("acme-new")))
                .isInstanceOf(RequestedNameAlreadyUsedException.class);
        verify(tenancyService, never()).submitApplication(any(), any(), any(), any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void get_returnsExactApplicationOutsideTheListPage() {
        UUID applicationId = UUID.randomUUID();
        Organization application = pendingApplication(applicationId, "acme");
        when(tenancyService.findApplication(applicationId)).thenReturn(Optional.of(application));

        TenantApplicationResponse response = service.get(applicationId);

        assertThat(response.publicId()).isEqualTo(applicationId);
        assertThat(response.status()).isEqualTo("PENDING");
        verify(tenancyService).findApplication(applicationId);
    }

    private Organization pendingApplication(UUID publicId, String code) {
        Organization application = new Organization();
        application.setPublicId(publicId);
        application.setName("Acme School");
        application.setOrganizationType("SCHOOL");
        application.setCode(code);
        application.setContactEmail("contact@acme.example");
        application.setTaxCode("0123456789");
        return application;
    }

    @Test
    void approve_activatesTenantAndCreatesHostAdmin_marksApplicationApproved() {
        UUID applicationId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        Organization application = pendingApplication(applicationId, "acme");

        User hostAdminUser = new User();
        hostAdminUser.setPublicId(UUID.randomUUID());
        hostAdminUser.setUsername("contact@acme.example");
        hostAdminUser.setRoles(Set.of(Role.HOST_ADMIN));
        HostAdminCreated hostAdmin = new HostAdminCreated(hostAdminUser, "Gener4ted!");

        when(tenancyService.findApplicationForUpdate(applicationId)).thenReturn(Optional.of(application));
        when(platformSettingService.getInteger(BillingConstants.FREE_STUDENT_LIMIT_SETTING_KEY)).thenReturn(75);
        when(tenancyService.approveApplication(application, reviewerId, 75)).thenAnswer(invocation -> {
            application.setStudentLimit(75);
            application.approve(reviewerId, Instant.parse("2026-10-17T00:00:00Z"));
            return application;
        });
        when(identityService.createHostAdmin(applicationId, "contact@acme.example")).thenReturn(hostAdmin);

        CurrentUser caller = new CurrentUser(reviewerId, null, List.of("PLATFORM_MANAGER"));
        service.approve(applicationId, caller);

        assertThat(TenantApplicationMapper.statusOf(application)).isEqualTo(TenantApplicationStatus.APPROVED);
        assertThat(application.getStatus()).isEqualTo(OrganizationStatus.ACTIVE);
        assertThat(application.getReviewedBy()).isEqualTo(reviewerId);
        assertThat(application.getStudentLimit()).isEqualTo(75);
        verify(tenancyService).approveApplication(application, reviewerId, 75);
        ArgumentCaptor<TenantApplicationApprovedEvent> eventCaptor =
                ArgumentCaptor.forClass(TenantApplicationApprovedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().hostAdminUsername()).isEqualTo("contact@acme.example");
        assertThat(eventCaptor.getValue().hostAdminPassword()).isEqualTo("Gener4ted!");
    }

    @Test
    void approve_unknownApplication_throwsNotFound() {
        UUID applicationId = UUID.randomUUID();
        when(tenancyService.findApplicationForUpdate(applicationId)).thenReturn(Optional.empty());

        CurrentUser caller = new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"));

        assertThatThrownBy(() -> service.approve(applicationId, caller))
                .isInstanceOf(TenantApplicationNotFoundException.class);
    }

    @Test
    void approve_alreadyReviewedApplication_throwsAndCreatesNothing() {
        UUID applicationId = UUID.randomUUID();
        Organization application = pendingApplication(applicationId, "acme");
        application.approve(UUID.randomUUID(), Instant.parse("2026-10-17T00:00:00Z"));
        when(tenancyService.findApplicationForUpdate(applicationId)).thenReturn(Optional.of(application));

        CurrentUser caller = new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"));

        assertThatThrownBy(() -> service.approve(applicationId, caller))
                .isInstanceOf(ApplicationNotPendingException.class);
        verify(tenancyService, never()).approveApplication(any(), any(), anyInt());
        verify(identityService, never()).createHostAdmin(any(), any());
    }

    @Test
    void reject_marksApplicationRejectedWithReason() {
        UUID applicationId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        Organization application = pendingApplication(applicationId, "acme");
        when(tenancyService.findApplicationForUpdate(applicationId)).thenReturn(Optional.of(application));
        rejectingApplicationOnReview(application);

        CurrentUser caller = new CurrentUser(reviewerId, null, List.of("PLATFORM_ADMIN"));
        TenantApplicationResponse response = service.reject(applicationId,
                new RejectApplicationRequest("Brand name conflict"), caller);

        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(response.rejectReason()).isEqualTo("Brand name conflict");
        assertThat(application.getReviewedBy()).isEqualTo(reviewerId);
        verify(tenancyService).rejectApplication(application, reviewerId, "Brand name conflict");
        verify(eventPublisher).publishEvent(any(Object.class));
    }

    @Test
    void reject_alreadyReviewedApplication_throws() {
        UUID applicationId = UUID.randomUUID();
        Organization application = pendingApplication(applicationId, "acme");
        application.reject(UUID.randomUUID(), Instant.parse("2026-10-17T00:00:00Z"), "first reason");
        when(tenancyService.findApplicationForUpdate(applicationId)).thenReturn(Optional.of(application));

        CurrentUser caller = new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"));

        assertThatThrownBy(() -> service.reject(applicationId,
                new RejectApplicationRequest("second reason"), caller))
                .isInstanceOf(ApplicationNotPendingException.class);
        verify(tenancyService, never()).rejectApplication(any(), any(), any());
    }

    @Test
    void approve_mapsApprovalTimeTaxConstraintToBillingConflict() {
        UUID applicationId = UUID.randomUUID();
        Organization application = pendingApplication(applicationId, "acme");
        when(tenancyService.findApplicationForUpdate(applicationId)).thenReturn(Optional.of(application));
        when(platformSettingService.getInteger(BillingConstants.FREE_STUDENT_LIMIT_SETTING_KEY)).thenReturn(75);
        when(tenancyService.approveApplication(eq(application), any(), anyInt()))
                .thenThrow(new DataIntegrityViolationException("uq_org_tax_code_approved"));

        assertThatThrownBy(() -> service.approve(applicationId,
                new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"))))
                .isInstanceOf(RequestedTaxCodeAlreadyUsedException.class);
        verify(identityService, never()).createHostAdmin(any(), any());
        assertThat(TenantApplicationMapper.statusOf(application)).isEqualTo(TenantApplicationStatus.PENDING);
    }

    @Test
    void reject_trimsReasonAndDirectBlankReasonIsUnprocessable() {
        UUID applicationId = UUID.randomUUID();
        Organization application = pendingApplication(applicationId, "acme");
        when(tenancyService.findApplicationForUpdate(applicationId)).thenReturn(Optional.of(application));
        rejectingApplicationOnReview(application);

        service.reject(applicationId, new RejectApplicationRequest("  fix the tax code  "),
                new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN")));

        assertThat(application.getRejectReason()).isEqualTo("fix the tax code");
        UUID blankApplicationId = UUID.randomUUID();
        when(tenancyService.findApplicationForUpdate(blankApplicationId))
                .thenReturn(Optional.of(pendingApplication(blankApplicationId, "other-acme")));
        assertThatThrownBy(() -> service.reject(blankApplicationId, new RejectApplicationRequest("   "),
                new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"))))
                .isInstanceOf(TenantApplicationValidationException.class);
    }

    /** Makes the mocked tenancy module apply the real organization transition, as the production one does. */
    private void rejectingApplicationOnReview(Organization application) {
        when(tenancyService.rejectApplication(eq(application), any(), any())).thenAnswer(invocation -> {
            application.reject(invocation.getArgument(1), Instant.parse("2026-10-17T00:00:00Z"),
                    invocation.getArgument(2));
            return application;
        });
    }
}
