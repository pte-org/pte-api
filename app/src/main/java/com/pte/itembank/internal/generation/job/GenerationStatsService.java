package com.pte.itembank.internal.generation.job;

import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import com.pte.itembank.internal.generation.domain.ItemStage;
import com.pte.itembank.internal.generation.dto.GenerationStatsResponse;
import com.pte.itembank.internal.generation.config.QuestionGenerationProperties;
import com.pte.itembank.internal.generation.repository.GenerationJobItemRepository;
import com.pte.itembank.internal.generation.usage.GenerationUsage;
import com.pte.itembank.internal.generation.usage.GenerationUsageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Aggregates how generation items ended, so an admin can see what works without reading the database. */
@Service
public class GenerationStatsService {

    static final int DEFAULT_DAYS = 30;
    static final int MAX_DAYS = 365;
    /** Days in a picked range start and end at midnight in Vietnam, where the admins are. */
    static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000);
    private static final int COST_SCALE = 6;

    private final GenerationJobItemRepository items;
    private final GenerationUsageRepository usage;
    private final QuestionGenerationProperties.Llm llm;
    private final Clock clock;

    public GenerationStatsService(GenerationJobItemRepository items, GenerationUsageRepository usage,
                                  QuestionGenerationProperties properties, Clock clock) {
        this.items = items;
        this.usage = usage;
        this.llm = properties.getLlm();
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public GenerationStatsResponse stats(Integer requestedDays) {
        int days = requestedDays == null ? DEFAULT_DAYS : Math.min(Math.max(requestedDays, 1), MAX_DAYS);
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, ZONE);
        return aggregate(days, items.countOutcomesBetween(now.minus(Duration.ofDays(days)), now),
                tokens(usage.sum(today.minusDays(days - 1L), today)));
    }

    /**
     * The same figures for the jobs created from {@code from} to {@code to}, both days included. Reversed days are
     * read the other way round, and a range longer than {@link #MAX_DAYS} keeps its last {@link #MAX_DAYS} days.
     */
    @Transactional(readOnly = true)
    public GenerationStatsResponse stats(LocalDate from, LocalDate to) {
        LocalDate first = from.isAfter(to) ? to : from;
        LocalDate last = from.isAfter(to) ? from : to;
        if (ChronoUnit.DAYS.between(first, last) >= MAX_DAYS) {
            first = last.minusDays(MAX_DAYS - 1L);
        }
        int days = (int) ChronoUnit.DAYS.between(first, last) + 1;
        return aggregate(days, items.countOutcomesBetween(first.atStartOfDay(ZONE).toInstant(),
                last.plusDays(1).atStartOfDay(ZONE).toInstant()), tokens(usage.sum(first, last)));
    }

    private GenerationStatsResponse.TokenStats tokens(GenerationUsage spent) {
        BigDecimal cost = llm.getPriceInputPerMillionUsd().multiply(BigDecimal.valueOf(spent.tokensIn()))
                .add(llm.getPriceOutputPerMillionUsd().multiply(BigDecimal.valueOf(spent.tokensOut())))
                .divide(ONE_MILLION, COST_SCALE, RoundingMode.HALF_UP);
        return new GenerationStatsResponse.TokenStats(spent.calls(), spent.tokensIn(), spent.tokensOut(), cost);
    }

    private static GenerationStatsResponse aggregate(int days, List<Object[]> rows,
                                                     GenerationStatsResponse.TokenStats tokens) {
        int saved = 0;
        int failed = 0;
        int reviewRejected = 0;
        Map<String, int[]> byTaskType = new TreeMap<>();
        Map<String, Integer> failures = new TreeMap<>();
        for (Object[] row : rows) {
            String taskType = (String) row[0];
            boolean isSaved = row[1] == ItemStage.SAVED;
            String code = (String) row[2];
            int count = ((Number) row[3]).intValue();
            int[] totals = byTaskType.computeIfAbsent(taskType, key -> new int[2]);
            if (isSaved) {
                saved += count;
                totals[0] += count;
            } else {
                failed += count;
                totals[1] += count;
                String failureCode = code == null ? QuestionGenerationConstants.GENERATION_INTERNAL_ERROR : code;
                failures.merge(failureCode, count, Integer::sum);
                if (QuestionGenerationConstants.GENERATION_REVIEW_FAILED.equals(failureCode)) {
                    reviewRejected += count;
                }
            }
        }
        List<GenerationStatsResponse.TaskTypeStats> perType = byTaskType.entrySet().stream()
                .map(entry -> new GenerationStatsResponse.TaskTypeStats(entry.getKey(), entry.getValue()[0],
                        entry.getValue()[1]))
                .toList();
        List<GenerationStatsResponse.FailureStats> perFailure = failures.entrySet().stream()
                .map(entry -> new GenerationStatsResponse.FailureStats(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt(GenerationStatsResponse.FailureStats::count).reversed())
                .toList();
        return new GenerationStatsResponse(days, saved, failed, reviewRejected, perType, perFailure, tokens);
    }
}
