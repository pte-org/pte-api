package com.pte.identity.internal.service;

import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.domain.UserStatus;
import com.pte.identity.internal.dto.response.UserResponse;
import com.pte.identity.internal.exception.InvalidExamStaffQueryException;
import com.pte.identity.internal.mapper.UserMapper;
import com.pte.identity.internal.repository.UserRepository;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.web.PageMeta;
import com.pte.shared.web.PagedResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Tenant-scoped, server-side list/search for Proctor and Examiner accounts. */
@Service
public class ExamStaffQueryService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final Set<Role> STAFF_ROLES = EnumSet.of(Role.PROCTOR, Role.EXAMINER);

    private final UserRepository userRepository;

    public ExamStaffQueryService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public PagedResult<UserResponse> search(int requestedPage, int requestedSize, String search,
            String role, String status, String sort, String direction, CurrentUser caller) {
        int page = normalizePage(requestedPage);
        int size = normalizeSize(requestedSize);
        UUID tenantId = caller.tenantId();
        if (tenantId == null) {
            return emptyPage(page, size);
        }

        Role normalizedRole = parseRole(role);
        UserStatus normalizedStatus = parseStatus(status);
        String normalizedSearch = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        Sort.Direction normalizedDirection = parseDirection(direction);
        String sortProperty = parseSort(sort);
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(normalizedDirection, sortProperty)
                        .and(Sort.by(normalizedDirection, "publicId")));

        Page<User> result = userRepository.findPageForExamStaff(
                tenantId, STAFF_ROLES, normalizedRole, normalizedStatus, normalizedSearch, pageable);
        return new PagedResult<>(result.map(UserMapper::toResponse).getContent(),
                new PageMeta(result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages(),
                        result.isFirst(), result.isLast(), result.hasNext(), result.hasPrevious()));
    }

    private static Role parseRole(String value) {
        if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value.trim())) {
            return null;
        }
        try {
            Role role = Role.valueOf(value.trim().toUpperCase(Locale.ROOT));
            return STAFF_ROLES.contains(role) ? role : throwInvalid();
        } catch (IllegalArgumentException ex) {
            throw new InvalidExamStaffQueryException();
        }
    }

    private static UserStatus parseStatus(String value) {
        if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value.trim())) {
            return null;
        }
        try {
            return UserStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new InvalidExamStaffQueryException();
        }
    }

    private static String parseSort(String value) {
        if (value == null || value.isBlank()) {
            return "createdAt";
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "CREATED_AT" -> "createdAt";
            case "FULL_NAME" -> "fullName";
            case "EMAIL" -> "email";
            default -> throw new InvalidExamStaffQueryException();
        };
    }

    private static Sort.Direction parseDirection(String value) {
        if (value == null || value.isBlank()) {
            return Sort.Direction.DESC;
        }
        try {
            return Sort.Direction.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new InvalidExamStaffQueryException();
        }
    }

    private static Role throwInvalid() {
        throw new InvalidExamStaffQueryException();
    }

    private static int normalizePage(int requestedPage) {
        return Math.max(DEFAULT_PAGE, requestedPage);
    }

    private static int normalizeSize(int requestedSize) {
        if (requestedSize <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(requestedSize, MAX_SIZE);
    }

    private static PagedResult<UserResponse> emptyPage(int page, int size) {
        return new PagedResult<>(java.util.List.of(), new PageMeta(page, size, 0, 0,
                page == 0, true, false, page > 0));
    }
}
