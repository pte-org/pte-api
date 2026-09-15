package com.pte.admin.service;

import com.pte.admin.domain.exception.InvalidStudentRosterQueryException;
import com.pte.admin.dto.response.StudentRosterRowResponse;
import com.pte.admin.repository.StudentRosterEntryRepository;
import com.pte.common.security.CurrentUser;
import com.pte.common.web.PageMeta;
import com.pte.common.web.PagedResult;
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

    private final StudentRosterEntryRepository rosterRepository;

    public StudentRosterQueryService(StudentRosterEntryRepository rosterRepository) {
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
        String normalizedAssignmentStatus = parseAssignmentStatus(assignmentStatus);
        String normalizedSort = parseSort(sort);
        String normalizedDirection = parseDirection(direction);

        Page<StudentRosterRowResponse> result = rosterRepository.findPageForTenant(
                tenantId, normalizedSearch, programPublicId, classPublicId, normalizedAssignmentStatus,
                normalizedSort, normalizedDirection, PageRequest.of(page, size));
        return new PagedResult<>(result.getContent(), new PageMeta(result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), result.isFirst(), result.isLast(),
                result.hasNext(), result.hasPrevious()));
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

    private String parseAssignmentStatus(String value) {
        return parse(value, "ALL", "ALL", "ASSIGNED", "UNASSIGNED");
    }

    private String parseSort(String value) {
        return parse(value, "CREATED_AT", "CREATED_AT", "FULL_NAME", "STUDENT_CODE");
    }

    private String parseDirection(String value) {
        return parse(value, "DESC", "ASC", "DESC");
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
