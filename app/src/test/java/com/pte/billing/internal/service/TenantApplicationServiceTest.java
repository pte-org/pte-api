package com.pte.billing.internal.service;

import com.pte.billing.domain.TenantApplication;
import com.pte.billing.domain.enums.TenantApplicationStatus;
import com.pte.billing.TenantApplicationApprovedEvent;
import com.pte.billing.internal.dto.request.RejectApplicationRequest;
import com.pte.billing.internal.dto.request.SubmitApplicationRequest;
import com.pte.billing.internal.dto.response.TenantApplicationResponse;
import com.pte.billing.internal.constant.BillingConstants;
import com.pte.billing.internal.exception.ApplicationNotPendingException;
import com.pte.billing.internal.exception.RequestedCodeAlreadyUsedException;
import com.pte.billing.internal.exception.TenantApplicationNotFoundException;
import com.pte.billing.internal.repository.TenantApplicationRepository;
import com.pte.identity.domain.HostAdminCreated;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.IdentityService;
import com.pte.shared.security.CurrentUser;
import com.pte.tenancy.TenancyService;
import com.pte.tenancy.domain.Tenant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantApplicationServiceTest {

    @Mock
    private TenantApplicationRepository applicationRepository;

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
        service = new TenantApplicationService(applicationRepository, tenancyService, identityService,
                platformSettingService, eventPublisher);
    }

    private SubmitApplicationRequest submitRequest(String code) {
        return new SubmitApplicationRequest("Acme School", "SCHOOL", code, "contact@acme.example", null,
                "0123456789");
    }

    @Test
    void submit_savesApplicationAsPending() {
        when(tenancyService.existsByCode("acme")).thenReturn(false);
        when(applicationRepository.existsByRequestedCodeAndStatus("acme", TenantApplicationStatus.PENDING))
                .thenReturn(false);
        when(applicationRepository.saveAndFlush(any(TenantApplication.class))).thenAnswer(invocation -> {
            TenantApplication application = invocation.getArgument(0);
            application.setPublicId(UUID.randomUUID());
            return application;
        });

        service.submit(submitRequest("acme"));

        verify(eventPublisher).publishEvent(any(Object.class));
    }

    @Test
    void submit_codeAlreadyATenant_throwsWithoutSaving() {
        when(tenancyService.existsByCode("acme")).thenReturn(true);

        assertThatThrownBy(() -> service.submit(submitRequest("acme")))
                .isInstanceOf(RequestedCodeAlreadyUsedException.class);
        verify(applicationRepository, never()).saveAndFlush(any());
    }

    @Test
    void submit_codeHeldByAnotherPendingApplication_throwsWithoutSaving() {
        when(tenancyService.existsByCode("acme")).thenReturn(false);
        when(applicationRepository.existsByRequestedCodeAndStatus("acme", TenantApplicationStatus.PENDING))
                .thenReturn(true);

        assertThatThrownBy(() -> service.submit(submitRequest("acme")))
                .isInstanceOf(RequestedCodeAlreadyUsedException.class);
        verify(applicationRepository, never()).saveAndFlush(any());
    }

    private TenantApplication pendingApplication(UUID publicId, String code) {
        TenantApplication application = new TenantApplication();
        application.setPublicId(publicId);
        application.setOrgName("Acme School");
        application.setOrgType("SCHOOL");
        application.setRequestedCode(code);
        application.setContactEmail("contact@acme.example");
        application.setTaxCode("0123456789");
        return application;
    }

    @Test
    void approve_createsTenantAndHostAdmin_marksApplicationApproved() {
        UUID applicationId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        TenantApplication application = pendingApplication(applicationId, "acme");

        Tenant tenant = new Tenant();
        tenant.setPublicId(UUID.randomUUID());
        tenant.setCode("acme");

        User hostAdminUser = new User();
        hostAdminUser.setPublicId(UUID.randomUUID());
        hostAdminUser.setUsername("contact@acme.example");
        hostAdminUser.setRoles(Set.of(Role.HOST_ADMIN));
        HostAdminCreated hostAdmin = new HostAdminCreated(hostAdminUser, "Gener4ted!");

        when(applicationRepository.findByPublicId(applicationId)).thenReturn(Optional.of(application));
        when(platformSettingService.getInteger(BillingConstants.FREE_STUDENT_LIMIT_SETTING_KEY)).thenReturn(75);
        when(tenancyService.createTenant("Acme School", "SCHOOL", "acme", "0123456789", 75)).thenReturn(tenant);
        when(identityService.createHostAdmin(tenant.getPublicId(), "contact@acme.example")).thenReturn(hostAdmin);

        CurrentUser caller = new CurrentUser(reviewerId, null, List.of("PLATFORM_ADMIN"));
        service.approve(applicationId, caller);

        assertThat(application.getStatus()).isEqualTo(TenantApplicationStatus.APPROVED);
        assertThat(application.getReviewedBy()).isEqualTo(reviewerId);
        ArgumentCaptor<TenantApplicationApprovedEvent> eventCaptor =
                ArgumentCaptor.forClass(TenantApplicationApprovedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().hostAdminUsername()).isEqualTo("contact@acme.example");
        assertThat(eventCaptor.getValue().hostAdminPassword()).isEqualTo("Gener4ted!");
    }

    @Test
    void approve_unknownApplication_throwsNotFound() {
        UUID applicationId = UUID.randomUUID();
        when(applicationRepository.findByPublicId(applicationId)).thenReturn(Optional.empty());

        CurrentUser caller = new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"));

        assertThatThrownBy(() -> service.approve(applicationId, caller))
                .isInstanceOf(TenantApplicationNotFoundException.class);
    }

    @Test
    void approve_alreadyReviewedApplication_throwsAndCreatesNothing() {
        UUID applicationId = UUID.randomUUID();
        TenantApplication application = pendingApplication(applicationId, "acme");
        application.approve(UUID.randomUUID());
        when(applicationRepository.findByPublicId(applicationId)).thenReturn(Optional.of(application));

        CurrentUser caller = new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"));

        assertThatThrownBy(() -> service.approve(applicationId, caller))
                .isInstanceOf(ApplicationNotPendingException.class);
        verify(tenancyService, never()).createTenant(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyInt());
        verify(identityService, never()).createHostAdmin(any(), any());
    }

    @Test
    void reject_marksApplicationRejectedWithReason() {
        UUID applicationId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        TenantApplication application = pendingApplication(applicationId, "acme");
        when(applicationRepository.findByPublicId(applicationId)).thenReturn(Optional.of(application));

        CurrentUser caller = new CurrentUser(reviewerId, null, List.of("PLATFORM_ADMIN"));
        TenantApplicationResponse response = service.reject(applicationId,
                new RejectApplicationRequest("Brand name conflict"), caller);

        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(response.rejectReason()).isEqualTo("Brand name conflict");
        assertThat(application.getReviewedBy()).isEqualTo(reviewerId);
        verify(eventPublisher).publishEvent(any(Object.class));
    }

    @Test
    void reject_alreadyReviewedApplication_throws() {
        UUID applicationId = UUID.randomUUID();
        TenantApplication application = pendingApplication(applicationId, "acme");
        application.reject(UUID.randomUUID(), "first reason");
        when(applicationRepository.findByPublicId(applicationId)).thenReturn(Optional.of(application));

        CurrentUser caller = new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"));

        assertThatThrownBy(() -> service.reject(applicationId,
                new RejectApplicationRequest("second reason"), caller))
                .isInstanceOf(ApplicationNotPendingException.class);
    }
}
