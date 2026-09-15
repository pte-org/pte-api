package com.pte.iam.controller;

import com.pte.common.security.InternalExportScope;
import com.pte.common.web.ApiResponse;
import com.pte.common.web.ExportPage;
import com.pte.common.web.KeysetCursor;
import com.pte.iam.controller.dto.StudentExportItem;
import com.pte.iam.domain.User;
import com.pte.iam.domain.enums.Role;
import com.pte.iam.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Internal-only source for rebuilding Admin's student roster projection. */
@RestController
@RequestMapping("/internal")
@PreAuthorize("hasRole('INTERNAL_SERVICE')")
public class InternalExportController {

    private static final int DEFAULT_LIMIT = 200;
    private static final int MAX_LIMIT = 500;

    private final UserRepository userRepository;

    public InternalExportController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/students/export")
    public ApiResponse<ExportPage<StudentExportItem>> exportStudents(Authentication authentication,
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) String since,
            @RequestParam(required = false) Integer limit) {
        UUID scopedTenantId = InternalExportScope.resolve(authentication, tenantId);
        int pageSize = boundedLimit(limit);
        KeysetCursor.Cursor cursor = KeysetCursor.decode(since);
        Instant cursorTime = cursor == null ? Instant.EPOCH : cursor.updatedAt();
        UUID cursorId = cursor == null ? new UUID(0, 0) : cursor.publicId();

        List<User> rows = userRepository.findStudentsForExport(scopedTenantId, cursorTime, cursorId,
                Role.STUDENT, PageRequest.of(0, pageSize));
        List<StudentExportItem> items = rows.stream()
                .map(user -> new StudentExportItem(user.getPublicId(), user.getTenantId(), user.getEmail(),
                        user.getFullName(), user.getStudentCode(), user.getPhone(), user.getStatus().name(),
                        user.getCreatedAt()))
                .toList();
        User last = rows.isEmpty() ? null : rows.get(rows.size() - 1);
        String nextCursor = last == null ? null : KeysetCursor.encode(last.getCreatedAt(), last.getPublicId());
        return ApiResponse.success(new ExportPage<>(items, nextCursor, rows.size() == pageSize));
    }

    private int boundedLimit(Integer requested) {
        if (requested == null || requested <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(requested, MAX_LIMIT);
    }
}
