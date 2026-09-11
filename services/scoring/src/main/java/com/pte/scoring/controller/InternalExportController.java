package com.pte.scoring.controller;

import com.pte.common.security.InternalExportScope;
import com.pte.common.web.ApiResponse;
import com.pte.common.web.ExportPage;
import com.pte.common.web.KeysetCursor;
import com.pte.scoring.controller.dto.AnswerScoredExportItem;
import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.repository.ScoringAnswerRepository;
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
 * NOT the steady-state event path (that stays {@code @RabbitListener}s, Phase 6).
 */
@RestController
@RequestMapping("/internal")
@PreAuthorize("hasRole('INTERNAL_SERVICE')")
public class InternalExportController {

    private static final int DEFAULT_LIMIT = 200;
    private static final int MAX_LIMIT = 500;

    private final ScoringAnswerRepository scoringAnswerRepository;

    public InternalExportController(ScoringAnswerRepository scoringAnswerRepository) {
        this.scoringAnswerRepository = scoringAnswerRepository;
    }

    @GetMapping("/answers-scored/export")
    public ApiResponse<ExportPage<AnswerScoredExportItem>> exportAnswersScored(Authentication authentication,
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) String since,
            @RequestParam(required = false) Integer limit) {
        UUID scopedTenantId = InternalExportScope.resolve(authentication, tenantId);
        int pageSize = boundedLimit(limit);
        KeysetCursor.Cursor cursor = KeysetCursor.decode(since);
        Instant cursorTime = cursor != null ? cursor.updatedAt() : Instant.EPOCH;
        UUID cursorId = cursor != null ? cursor.publicId() : new UUID(0, 0);

        List<ScoringAnswer> rows = scoringAnswerRepository.findScoredForExport(
                scopedTenantId, cursorTime, cursorId, PageRequest.of(0, pageSize));

        List<AnswerScoredExportItem> items = rows.stream()
                .map(s -> new AnswerScoredExportItem(s.getAnswerPublicId(), s.getAttemptPublicId(), s.getTenantId(),
                        s.getRawScore(), s.getUpdatedAt()))
                .toList();
        ScoringAnswer last = rows.isEmpty() ? null : rows.get(rows.size() - 1);
        String nextCursor = last == null ? null : KeysetCursor.encode(last.getUpdatedAt(), last.getPublicId());
        return ApiResponse.success(new ExportPage<>(items, nextCursor, rows.size() == pageSize));
    }

    private int boundedLimit(Integer requested) {
        if (requested == null || requested <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(requested, MAX_LIMIT);
    }
}
