package com.pte.practice.internal.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

/**
 * Picks the questions of one task type for a practice session: distinct, random, and questions the student has
 * never had before come first. Shuffling happens here rather than with SQL {@code RANDOM()} so it behaves the same
 * on Postgres and H2 and is deterministic in tests with a seeded generator.
 */
@Component
public class PracticeQuestionPicker {

    private final Supplier<RandomGenerator> randomSource;

    @Autowired
    public PracticeQuestionPicker() {
        this(ThreadLocalRandom::current);
    }

    PracticeQuestionPicker(Supplier<RandomGenerator> randomSource) {
        this.randomSource = randomSource;
    }

    public List<UUID> pick(Collection<UUID> candidates, Set<UUID> seen, int count) {
        return pick(candidates, seen, count, randomSource.get());
    }

    /** Same pick with a caller-supplied generator, so a seeded one gives the same questions every time. */
    public List<UUID> pick(Collection<UUID> candidates, Set<UUID> seen, int count, RandomGenerator random) {
        if (candidates == null || candidates.isEmpty() || count <= 0) {
            return List.of();
        }
        List<UUID> unseen = new ArrayList<>();
        List<UUID> alreadySeen = new ArrayList<>();
        for (UUID candidate : new LinkedHashSet<>(candidates)) {
            if (candidate == null) {
                continue;
            }
            (seen != null && seen.contains(candidate) ? alreadySeen : unseen).add(candidate);
        }
        shuffle(unseen, random);
        shuffle(alreadySeen, random);
        List<UUID> picked = new ArrayList<>(unseen);
        picked.addAll(alreadySeen);
        return List.copyOf(picked.subList(0, Math.min(count, picked.size())));
    }

    /** Fisher–Yates on the given (already copied) list. */
    private void shuffle(List<UUID> values, RandomGenerator random) {
        for (int index = values.size() - 1; index > 0; index--) {
            int swap = random.nextInt(index + 1);
            UUID current = values.get(index);
            values.set(index, values.get(swap));
            values.set(swap, current);
        }
    }
}
