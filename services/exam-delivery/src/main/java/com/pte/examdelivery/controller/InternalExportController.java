package com.pte.examdelivery.controller;

import com.pte.common.security.InternalExportScope;
import com.pte.common.web.ApiResponse;
import com.pte.common.web.ExportPage;
import com.pte.common.web.KeysetCursor;
import com.pte.examdelivery.controller.dto.AnswerExportItem;
import com.pte.examdelivery.controller.dto.AttemptExportItem;
import com.pte.examdelivery.domain.AttemptAnswer;
import com.pte.examdelivery.domain.ExamAttempt;
import com.pte.examdelivery.repository.AttemptAnswerRepository;
import com.pte.examdelivery.repository.ExamAttemptRepository;
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

/**
 * Service-to-service only (ROLE_INTERNAL_SERVICE, see {@code SecurityConfig}) —
 * powers reporting's read-model rebuild (rabbitmq-outbox-migration Phase 9),
 * NOT the steady-state event path (that stays {@code @RabbitListener}s, Phase 5).
 * {@code AttemptExportItem}/{@code AnswerExportItem} mirror exactly the
 * subset of {@code AttemptSubmitted}/{@code AnswerSubmitted} reporting's
 * projection actually consumes — not the full aggregate.
 */
@RestController
@RequestMapping("/internal")
@PreAuthorize("hasRole('INTERNAL_SERVICE')")
public class InternalExportController {

    private static final int DEFAULT_LIMIT = 200;
    private static final int MAX_LIMIT = 500;

    private final ExamAttemptRepository examAttemptRepository;
    private final AttemptAnswerRepository attemptAnswerRepository;

    public InternalExportController(ExamAttemptRepository examAttemptRepository,
            AttemptAnswerRepository attemptAnswerRepository) {
        this.examAttemptRepository = examAttemptRepository;
        this.attemptAnswerRepository = attemptAnswerRepository;
    }

    @GetMapping("/attempts/export")
    public ApiResponse<ExportPage<AttemptExportItem>> exportAttempts(Authentication authentication,
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) String since,
            @RequestParam(required = false) Integer limit) {
        UUID scopedTenantId = InternalExportScope.resolve(authentication, tenantId);
        int pageSize = boundedLimit(limit);
        KeysetCursor.Cursor cursor = KeysetCursor.decode(since);
        Instant cursorTime = cursor != null ? cursor.updatedAt() : Instant.EPOCH;
        UUID cursorId = cursor != null ? cursor.publicId() : new UUID(0, 0);

        List<ExamAttempt> rows = examAttemptRepository.findSubmittedForExport(
                scopedTenantId, cursorTime, cursorId, PageRequest.of(0, pageSize));

        List<AttemptExportItem> items = rows.stream()
                .map(a -> new AttemptExportItem(a.getPublicId(), a.getSessionPublicId(), a.getStudentPublicId(),
                        a.getTenantId(), a.getUpdatedAt()))
                .toList();
        return ApiResponse.success(toPage(items, rows.isEmpty() ? null : rows.get(rows.size() - 1), pageSize));
    }

    @GetMapping("/answers/export")
    public ApiResponse<ExportPage<AnswerExportItem>> exportAnswers(Authentication authentication,
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) String since,
            @RequestParam(required = false) Integer limit) {
        UUID scopedTenantId = InternalExportScope.resolve(authentication, tenantId);
        int pageSize = boundedLimit(limit);
        KeysetCursor.Cursor cursor = KeysetCursor.decode(since);
        Instant cursorTime = cursor != null ? cursor.updatedAt() : Instant.EPOCH;
        UUID cursorId = cursor != null ? cursor.publicId() : new UUID(0, 0);

        List<AttemptAnswer> rows = attemptAnswerRepository.findSubmittedForExport(
                scopedTenantId, cursorTime, cursorId, PageRequest.of(0, pageSize));

        List<AnswerExportItem> items = rows.stream()
                .map(a -> new AnswerExportItem(a.getPublicId(), a.getAttempt().getPublicId(),
                        a.getAttempt().getTenantId(), a.getPinnedItem().getTaskType(), a.getUpdatedAt()))
                .toList();
        AttemptAnswer last = rows.isEmpty() ? null : rows.get(rows.size() - 1);
        String nextCursor = last == null ? null : KeysetCursor.encode(last.getUpdatedAt(), last.getPublicId());
        return ApiResponse.success(new ExportPage<>(items, nextCursor, rows.size() == pageSize));
    }

    private ExportPage<AttemptExportItem> toPage(List<AttemptExportItem> items, ExamAttempt last, int pageSize) {
        String nextCursor = last == null ? null : KeysetCursor.encode(last.getUpdatedAt(), last.getPublicId());
        return new ExportPage<>(items, nextCursor, items.size() == pageSize);
    }

    private int boundedLimit(Integer requested) {
        if (requested == null || requested <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(requested, MAX_LIMIT);
    }
}
