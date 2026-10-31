package com.pte.billing.internal.service.license;

import com.pte.billing.domain.LicenseCode;
import com.pte.billing.domain.ServicePlan;
import com.pte.billing.domain.Subscription;
import com.pte.billing.domain.enums.LicenseCodeStatus;
import com.pte.billing.internal.constant.BillingConstants;
import com.pte.billing.internal.dto.response.AdminLicenseCodeSummary;
import com.pte.billing.internal.exception.license.LicenseCodeException;
import com.pte.billing.internal.repository.LicenseCodeRepository;
import com.pte.billing.internal.repository.ServicePlanRepository;
import com.pte.billing.internal.repository.SubscriptionRepository;
import com.pte.shared.web.PageMeta;
import com.pte.shared.web.PagedResult;
import com.pte.tenancy.TenancyService;
import com.pte.tenancy.TenantSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Masked admin reads: paging, single lookup and plan/recipient enrichment. */
class LicenseCodeAdminQuery {

    private static final int DEFAULT_ADMIN_PAGE_SIZE = 25;
    private static final int MAX_ADMIN_PAGE_SIZE = 100;

    private final LicenseCodeRepository licenseCodeRepository;
    private final ServicePlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final TenancyService tenancyService;
    private final Clock clock;

    LicenseCodeAdminQuery(LicenseCodeRepository licenseCodeRepository, ServicePlanRepository planRepository,
            SubscriptionRepository subscriptionRepository, TenancyService tenancyService, Clock clock) {
        this.licenseCodeRepository = licenseCodeRepository;
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.tenancyService = tenancyService;
        this.clock = clock;
    }

    PagedResult<AdminLicenseCodeSummary> listForAdmin(Integer requestedPage, Integer requestedSize,
            String requestedStatus, UUID planId, UUID tenantId, String requestedSearch) {
        int page = requestedPage == null ? 0 : requestedPage;
        int size = requestedSize == null ? DEFAULT_ADMIN_PAGE_SIZE : requestedSize;
        if (page < 0) {
            throw new LicenseCodeException(HttpStatus.BAD_REQUEST, BillingConstants.LICENSE_CODE_PAGE_INVALID);
        }
        if (size < 1 || size > MAX_ADMIN_PAGE_SIZE) {
            throw new LicenseCodeException(HttpStatus.BAD_REQUEST, BillingConstants.LICENSE_CODE_PAGE_SIZE_INVALID);
        }
        LicenseCodeStatus status = parseAdminStatus(requestedStatus);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        String normalizedSearch = requestedSearch == null ? null : requestedSearch.trim();
        UUID searchId = parseUuid(normalizedSearch);
        if (normalizedSearch != null && !normalizedSearch.isBlank() && searchId == null) {
            return new PagedResult<>(List.of(), new PageMeta(page, size, 0, 0, page == 0, true, false, page > 0));
        }
        Page<LicenseCode> result = searchId == null
                ? licenseCodeRepository.findAdminPage(status, planId, tenantId, now, PageRequest.of(page, size))
                : licenseCodeRepository.findAdminPageByIdentifier(status, planId, tenantId, now, searchId,
                        PageRequest.of(page, size));
        return new PagedResult<>(adminSummaries(result.getContent(), now),
                new PageMeta(result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages(),
                        result.isFirst(), result.isLast(), result.hasNext(), result.hasPrevious()));
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    AdminLicenseCodeSummary getForAdmin(UUID publicId) {
        LicenseCode code = licenseCodeRepository.findByPublicId(publicId)
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> new LicenseCodeException(HttpStatus.NOT_FOUND,
                        BillingConstants.LICENSE_CODE_NOT_FOUND));
        return adminSummaries(List.of(code), clock.instant().truncatedTo(ChronoUnit.MICROS)).getFirst();
    }

    AdminLicenseCodeSummary lookupForAdmin(String rawCode) {
        String codeValue = LicenseCodeAccessPolicy.normalizeCode(rawCode);
        LicenseCode code = licenseCodeRepository.findByCode(codeValue)
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> new LicenseCodeException(HttpStatus.NOT_FOUND,
                        BillingConstants.LICENSE_CODE_LOOKUP_NOT_FOUND));
        return adminSummaries(List.of(code), clock.instant().truncatedTo(ChronoUnit.MICROS)).getFirst();
    }

    private LicenseCodeStatus parseAdminStatus(String rawStatus) {
        if (rawStatus == null) {
            return null;
        }
        if (rawStatus.isBlank()) {
            throw new LicenseCodeException(HttpStatus.BAD_REQUEST, BillingConstants.LICENSE_CODE_STATUS_INVALID);
        }
        try {
            return LicenseCodeStatus.valueOf(rawStatus.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new LicenseCodeException(HttpStatus.BAD_REQUEST, BillingConstants.LICENSE_CODE_STATUS_INVALID);
        }
    }

    private List<AdminLicenseCodeSummary> adminSummaries(List<LicenseCode> codes, Instant now) {
        if (codes.isEmpty()) {
            return List.of();
        }
        Set<UUID> planIds = codes.stream().map(LicenseCode::getPlanId).collect(Collectors.toSet());
        Map<UUID, ServicePlan> plans = planIds.isEmpty() ? Map.of() : planRepository.findByPublicIdIn(planIds).stream()
                .collect(Collectors.toUnmodifiableMap(ServicePlan::getPublicId, Function.identity()));
        Set<UUID> tenantIds = codes.stream().map(LicenseCode::getRedeemedByTenantId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, TenantSummary> recipients = tenancyService.findTenantSummaries(tenantIds);
        Set<UUID> subscriptionIds = codes.stream().map(LicenseCode::getSubscriptionId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, Subscription> subscriptions = subscriptionIds.isEmpty() ? Map.of()
                : subscriptionRepository.findByPublicIdIn(subscriptionIds).stream()
                        .collect(Collectors.toUnmodifiableMap(Subscription::getPublicId, Function.identity()));
        return codes.stream()
                .map(code -> {
                    UUID recipientId = code.getRedeemedByTenantId();
                    return AdminLicenseCodeSummary.from(code,
                            LicenseCodeStateResolver.resolve(code, now),
                            plans.get(code.getPlanId()),
                            recipientId == null ? null : recipients.get(recipientId),
                            code.getSubscriptionId() == null ? null : subscriptions.get(code.getSubscriptionId()));
                })
                .toList();
    }
}
