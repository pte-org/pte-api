package com.pte.itembank.internal.generation.usage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Counts the tokens of each successful provider call under its Vietnam day. Counting is best effort: a database
 * problem is logged and never fails the generation it measures.
 */
@Component
public class GenerationUsageRecorder {

    /** The admins' calendar; the stats screen picks days on it. */
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Logger LOG = LoggerFactory.getLogger(GenerationUsageRecorder.class);

    private final GenerationUsageRepository repository;
    private final Clock clock;

    public GenerationUsageRecorder(GenerationUsageRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void record(String role, String model, GenerationUsage usage) {
        try {
            repository.add(LocalDate.ofInstant(clock.instant(), ZONE), role, model, usage);
        } catch (RuntimeException failure) {
            LOG.warn("Could not count generation tokens: {}", failure.getClass().getSimpleName());
        }
    }
}
