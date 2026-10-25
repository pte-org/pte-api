package com.pte.scoring.internal.service.grading;

import com.pte.attempt.dto.response.AttemptGradingCandidateView;
import com.pte.scoring.dto.request.GradingCohortDispositionRequest;
import com.pte.scoring.internal.constant.GradingConstants;
import com.pte.scoring.internal.exception.GradingCohortException;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Pure rules for freezing a grading cohort: the preview fingerprint and the outstanding-paper dispositions. */
final class GradingCohortFreezeRules {

    private GradingCohortFreezeRules() {
    }

    static Map<UUID, String> validateDispositions(List<AttemptGradingCandidateView> candidates,
            List<GradingCohortDispositionRequest> requested) {
        Map<UUID, String> dispositions = new LinkedHashMap<>();
        if (requested != null) {
            for (GradingCohortDispositionRequest disposition : requested) {
                if (disposition == null || disposition.attemptPublicId() == null
                        || disposition.reason() == null || disposition.reason().isBlank()
                        || disposition.reason().trim().length() > GradingConstants.DISPOSITION_REASON_LIMIT
                        || dispositions.putIfAbsent(disposition.attemptPublicId(), disposition.reason().trim()) != null) {
                    throw error(HttpStatus.BAD_REQUEST, GradingConstants.INVALID_DISPOSITION,
                            GradingConstants.INVALID_DISPOSITION_MESSAGE);
                }
            }
        }
        Map<UUID, AttemptGradingCandidateView> byId = candidates.stream().collect(Collectors.toMap(
                AttemptGradingCandidateView::attemptPublicId, Function.identity()));
        for (UUID attemptId : dispositions.keySet()) {
            AttemptGradingCandidateView candidate = byId.get(attemptId);
            if (candidate == null || candidate.submitted()) {
                throw error(HttpStatus.BAD_REQUEST, GradingConstants.INVALID_DISPOSITION,
                        GradingConstants.INVALID_DISPOSITION_MESSAGE);
            }
        }
        List<UUID> outstanding = candidates.stream().filter(candidate -> !candidate.submitted())
                .map(AttemptGradingCandidateView::attemptPublicId).toList();
        if (!dispositions.keySet().containsAll(outstanding) || dispositions.size() != outstanding.size()) {
            throw error(HttpStatus.CONFLICT, GradingConstants.OUTSTANDING_DISPOSITION_REQUIRED,
                    GradingConstants.OUTSTANDING_DISPOSITION_REQUIRED_MESSAGE);
        }
        return dispositions;
    }

    static String previewVersion(UUID sessionPublicId, List<AttemptGradingCandidateView> candidates) {
        String input = sessionPublicId + "|" + candidates.stream()
                .sorted(Comparator.comparing(AttemptGradingCandidateView::attemptPublicId))
                .map(candidate -> candidate.attemptPublicId() + "|" + candidate.studentPublicId() + "|"
                        + candidate.status() + "|" + candidate.versionToken())
                .collect(Collectors.joining(";"));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(GradingConstants.SHA256_UNAVAILABLE, ex);
        }
    }

    static GradingCohortException error(HttpStatus status, String code, String message) {
        return new GradingCohortException(status, code, null, message);
    }
}
