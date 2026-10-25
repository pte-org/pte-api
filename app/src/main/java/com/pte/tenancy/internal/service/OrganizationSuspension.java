package com.pte.tenancy.internal.service;

import com.pte.shared.security.CurrentUser;
import com.pte.tenancy.TenantSuspensionDurationProvider;
import com.pte.tenancy.TenantSuspensionImpact;
import com.pte.tenancy.TenantSuspensionImpactProvider;
import com.pte.tenancy.domain.Organization;
import com.pte.tenancy.domain.enums.OrganizationStatus;
import com.pte.tenancy.internal.constant.TenancyConstants;
import com.pte.tenancy.internal.dto.request.SuspendTenantRequest;
import com.pte.tenancy.internal.dto.response.OrganizationResponse;
import com.pte.tenancy.internal.exception.TenantNotFoundException;
import com.pte.tenancy.internal.exception.TenantSuspensionImpactStaleException;
import com.pte.tenancy.internal.mapper.OrganizationMapper;
import com.pte.tenancy.internal.repository.OrganizationRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Time-boxed suspension of an organization: impact preview, digest-confirmed suspend and the default duration. */
class OrganizationSuspension {

    private final OrganizationRepository organizationRepository;
    private final OrganizationOperationAudit operationAudit;
    private final TenantSuspensionDurationProvider suspensionDurationProvider;
    private final TenantSuspensionImpactProvider suspensionImpactProvider;
    private final Clock clock;

    OrganizationSuspension(OrganizationRepository organizationRepository, OrganizationOperationAudit operationAudit,
            TenantSuspensionDurationProvider suspensionDurationProvider,
            TenantSuspensionImpactProvider suspensionImpactProvider, Clock clock) {
        this.organizationRepository = organizationRepository;
        this.operationAudit = operationAudit;
        this.suspensionDurationProvider = suspensionDurationProvider;
        this.suspensionImpactProvider = suspensionImpactProvider;
        this.clock = clock;
    }

    OrganizationResponse suspend(UUID publicId, Integer requestedDays, CurrentUser caller) {
        Organization organization = organizationRepository.findByPublicId(publicId)
                .orElseThrow(TenantNotFoundException::new);
        int days = resolveSuspensionDays(requestedDays);
        if (organization.getStatus() == OrganizationStatus.SUSPENDED) {
            return OrganizationMapper.toResponse(organization);
        }
        Instant now = clock.instant();
        organization.suspendUntil(days == 0 ? null : now.plus(Duration.ofDays(days)));
        operationAudit.audit(caller, TenancyConstants.EVENT_TENANT_SUSPENDED, publicId,
                TenancyConstants.AUDIT_TENANT_SUSPENDED_SUMMARY);
        return OrganizationMapper.toResponse(organization);
    }

    OrganizationResponse suspendWithConfirmation(UUID publicId, SuspendTenantRequest request,
            CurrentUser caller) {
        Organization organization = organizationRepository.findWithLockByPublicId(publicId)
                .orElseThrow(TenantNotFoundException::new);
        if (organization.getStatus() == OrganizationStatus.SUSPENDED) {
            return OrganizationMapper.toResponse(organization);
        }
        int days = resolveSuspensionDays(request.days());
        Instant now = clock.instant();
        Instant suspendedUntil = days == 0 ? null : now.plus(Duration.ofDays(days));
        if (suspensionImpactProvider == null) {
            throw new TenantSuspensionImpactStaleException();
        }
        TenantSuspensionImpact liveImpact = suspensionImpactProvider.preview(publicId, now, suspendedUntil);
        if (liveImpact.impactDigest() == null || request.impactDigest() == null
                || !MessageDigest.isEqual(request.impactDigest().getBytes(StandardCharsets.UTF_8),
                        liveImpact.impactDigest().getBytes(StandardCharsets.UTF_8))) {
            throw new TenantSuspensionImpactStaleException();
        }
        organization.suspendUntil(suspendedUntil);
        operationAudit.audit(caller, TenancyConstants.EVENT_TENANT_SUSPENDED, publicId,
                TenancyConstants.AUDIT_TENANT_SUSPENDED_SUMMARY);
        return OrganizationMapper.toResponse(organization);
    }

    TenantSuspensionImpact suspensionImpact(UUID publicId, Integer requestedDays) {
        organizationRepository.findByPublicId(publicId).orElseThrow(TenantNotFoundException::new);
        int days = resolveSuspensionDays(requestedDays);
        Instant now = clock.instant();
        Instant suspendedUntil = days == 0 ? null : now.plus(Duration.ofDays(days));
        if (suspensionImpactProvider == null) {
            return new TenantSuspensionImpact(publicId, days, suspendedUntil, 0, 0, List.of());
        }
        return suspensionImpactProvider.preview(publicId, now, suspendedUntil);
    }

    private int resolveSuspensionDays(Integer requestedDays) {
        int days = requestedDays == null
                ? (suspensionDurationProvider == null ? 0 : suspensionDurationProvider.defaultSuspensionDays())
                : requestedDays;
        if (days < 0) {
            throw new com.pte.tenancy.internal.exception.TenantSuspensionDaysInvalidException();
        }
        return days;
    }
}
