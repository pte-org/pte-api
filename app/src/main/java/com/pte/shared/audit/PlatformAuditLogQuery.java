package com.pte.shared.audit;

import com.pte.shared.audit.domain.AuditLog;
import com.pte.shared.audit.dto.AuditLogDetailResponse;
import com.pte.shared.audit.dto.AuditLogResponse;
import com.pte.shared.audit.internal.exception.AuditLogNotFoundException;
import com.pte.shared.audit.internal.repository.AuditLogRepository;
import com.pte.shared.web.PagedResult;
import com.pte.shared.audit.internal.repository.PlatformAuditSpecifications;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;

/**
 * Read side of the platform audit trail (rows without a tenant). Only platform staff reach it; the controller
 * enforces that, and tenant rows are excluded by every query here.
 */
@Service
public class PlatformAuditLogQuery {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    /** Newest first; the id breaks ties between rows written in the same instant. */
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final AuditLogRepository auditLogRepository;

    public PlatformAuditLogQuery(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public PagedResult<AuditLogResponse> list(PlatformAuditFilter filter, int requestedPage, int requestedSize) {
        int page = Math.max(requestedPage, 0);
        int size = requestedSize <= 0 ? DEFAULT_SIZE : Math.min(requestedSize, MAX_SIZE);
        return AuditLogResponses.toPage(auditLogRepository.findAll(
                PlatformAuditSpecifications.platformRows(
                        blankToNull(filter.aggregateType()),
                        blankToNull(filter.aggregateId()),
                        filter.actorId(),
                        normalizedSearch(filter.search()),
                        startOf(filter.createdFrom()),
                        endOf(filter.createdTo())),
                PageRequest.of(page, size, NEWEST_FIRST)));
    }

    @Transactional(readOnly = true)
    public AuditLogDetailResponse detail(UUID publicId) {
        AuditLog auditLog = auditLogRepository.findByPublicIdAndTenantIdIsNull(publicId)
                .orElseThrow(AuditLogNotFoundException::new);
        return new AuditLogDetailResponse(AuditLogResponses.toResponse(auditLog),
                parse(auditLog.getBeforeState()), parse(auditLog.getAfterState()));
    }

    private static JsonNode parse(String json) {
        if (json == null) {
            return null;
        }
        try {
            return MAPPER.readTree(json);
        } catch (JacksonException exception) {
            // The column is jsonb, so stored text is always valid JSON; this only guards a manual DB edit.
            return null;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalizedSearch(String search) {
        String trimmed = blankToNull(search);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private static Instant startOf(LocalDate date) {
        return date == null ? null : date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private static Instant endOf(LocalDate date) {
        return date == null ? null : date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
