package com.pte.enrollment.internal.service;

import com.pte.enrollment.internal.dto.response.StudentRosterRowResponse;
import com.pte.enrollment.internal.exception.InvalidStudentRosterQueryException;
import com.pte.enrollment.internal.repository.StudentRosterRepository;
import com.pte.enrollment.internal.repository.StudentRosterRow;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.web.PageMeta;
import com.pte.shared.web.PagedResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

/** Tenant-scoped search, filtering, deterministic sorting, and pagination. */
@Service
public class StudentRosterQueryService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final StudentRosterRepository rosterRepository;

    public StudentRosterQueryService(StudentRosterRepository rosterRepository) {
        this.rosterRepository = rosterRepository;
    }

    @Transactional(readOnly = true)
    public PagedResult<StudentRosterRowResponse> search(int requestedPage, int requestedSize, String search,
            UUID programPublicId, UUID classPublicId, String assignmentStatus, String sort, String direction,
            CurrentUser caller) {
        UUID tenantId = caller.tenantId();
        if (tenantId == null) {
            return emptyPage(normalizePage(requestedPage), normalizeSize(requestedSize));
        }

        int page = normalizePage(requestedPage);
        int size = normalizeSize(requestedSize);
        String normalizedSearch = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        String normalizedAssignmentStatus = parse(assignmentStatus, "ALL", "ALL", "ASSIGNED", "UNASSIGNED");
        String normalizedSort = parse(sort, "CREATED_AT", "CREATED_AT", "FULL_NAME", "STUDENT_CODE");
        String normalizedDirection = parse(direction, "DESC", "ASC", "DESC");

        Page<StudentRosterRow> result = rosterRepository.findPageForTenant(
                tenantId, normalizedSearch, programPublicId, classPublicId, normalizedAssignmentStatus,
                normalizedSort, normalizedDirection, PageRequest.of(page, size));
        return new PagedResult<>(result.map(StudentRosterQueryService::toResponse).getContent(),
                new PageMeta(result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages(),
                        result.isFirst(), result.isLast(), result.hasNext(), result.hasPrevious()));
    }

    private static StudentRosterRowResponse toResponse(StudentRosterRow row) {
        return new StudentRosterRowResponse(toUuid(row.getStudentPublicId()), row.getEmail(), row.getFullName(),
                row.getStudentCode(), row.getPhone(), row.getStatus(), row.getCreatedAt(), toUuid(row.getProgramPublicId()),
                row.getProgramName(), toUuid(row.getClassPublicId()), row.getClassName());
    }

    private static UUID toUuid(String value) {
        return value == null ? null : UUID.fromString(value);
    }

    private int normalizePage(int requestedPage) {
        return Math.max(DEFAULT_PAGE, requestedPage);
    }

    private int normalizeSize(int requestedSize) {
        if (requestedSize <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(requestedSize, MAX_SIZE);
    }

    private String parse(String value, String defaultValue, String... allowed) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (String candidate : allowed) {
            if (candidate.equals(normalized)) {
                return normalized;
            }
        }
        throw new InvalidStudentRosterQueryException();
    }

    private PagedResult<StudentRosterRowResponse> emptyPage(int page, int size) {
        return new PagedResult<>(java.util.List.of(), new PageMeta(page, size, 0, 0,
                page == 0, true, false, page > 0));
    }
}
