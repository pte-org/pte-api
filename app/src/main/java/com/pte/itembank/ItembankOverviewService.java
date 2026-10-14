package com.pte.itembank;

import com.pte.itembank.domain.enums.PteSection;
import com.pte.itembank.domain.enums.QuestionPool;
import com.pte.itembank.domain.enums.QuestionStatus;
import com.pte.itembank.domain.enums.Visibility;
import com.pte.itembank.internal.repository.QuestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Read-only question bank figures for the platform dashboard. Separate from
 * {@link ItembankService} (whose {@code stats} contract is unchanged) and self-contained: it uses
 * only this module's question repository. It counts the same rows as {@code stats}: shared,
 * non-deleted questions of every revision, so a superseded revision shows as ARCHIVED. The bank
 * holds no approval timestamp (only a created-at and a "ever published" flag), so there is no
 * approval-time figure here, only current-state counts and a created-in-window count.
 */
@Service
public class ItembankOverviewService {

    private final QuestionRepository questionRepository;

    public ItembankOverviewService(QuestionRepository questionRepository) {
        this.questionRepository = questionRepository;
    }

    /**
     * Every map holds all of its enum keys (0 when empty); {@code bySection} also carries any other
     * section name found in stored data. {@code byPool} counts all questions, {@code approvedByPool}
     * only APPROVED ones, so a PRACTICE-pool item is never mistaken for exam stock.
     */
    public record QuestionBankCounts(long total, Map<String, Long> byStatus, Map<String, Long> bySection,
            Map<String, Long> byPool, Map<String, Long> approvedByPool) {
    }

    /** Counts by status, skill section and pool from one grouped query. */
    @Transactional(readOnly = true)
    public QuestionBankCounts counts() {
        Map<String, Long> byStatus = zeroed(QuestionStatus.values());
        Map<String, Long> bySection = zeroed(PteSection.values());
        Map<String, Long> byPool = zeroed(QuestionPool.values());
        Map<String, Long> approvedByPool = zeroed(QuestionPool.values());
        long total = 0L;
        for (Object[] row : questionRepository.countByStatusSectionAndPool(Visibility.SHARED)) {
            QuestionStatus status = (QuestionStatus) row[0];
            String section = (String) row[1];
            QuestionPool pool = (QuestionPool) row[2];
            long count = ((Number) row[3]).longValue();
            total += count;
            byStatus.merge(status.name(), count, Long::sum);
            bySection.merge(section, count, Long::sum);
            byPool.merge(pool.name(), count, Long::sum);
            if (status == QuestionStatus.APPROVED) {
                approvedByPool.merge(pool.name(), count, Long::sum);
            }
        }
        return new QuestionBankCounts(total, Collections.unmodifiableMap(byStatus),
                Collections.unmodifiableMap(bySection), Collections.unmodifiableMap(byPool),
                Collections.unmodifiableMap(approvedByPool));
    }

    /** Questions created in {@code [from, to)}, whatever their status is now. */
    @Transactional(readOnly = true)
    public long countCreatedBetween(Instant from, Instant to) {
        return questionRepository.countCreatedBetween(Visibility.SHARED, from, to);
    }

    private static Map<String, Long> zeroed(Enum<?>[] keys) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Enum<?> key : keys) {
            counts.put(key.name(), 0L);
        }
        return counts;
    }
}
