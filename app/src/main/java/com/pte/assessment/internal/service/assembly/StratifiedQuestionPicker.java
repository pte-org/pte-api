package com.pte.assessment.internal.service.assembly;

import com.pte.itembank.domain.enums.CefrLevel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Draws an exam's questions so that each skill follows a CEFR mix. For every skill the mix is turned into a
 * quota per level (largest-remainder rounding, so the quotas add up to the skill's question count); questions
 * are then taken task type by task type from the level with the most quota left. When a task type has no
 * question at the level it is due, the nearest level stands in; questions with no level come last. A mix that
 * stays within B1 to C2 never reaches below B1. The same seed always gives the same draw.
 */
public final class StratifiedQuestionPicker {

    private static final int PERCENT_TOTAL = 100;

    private StratifiedQuestionPicker() {
    }

    /** A task type of the exam, the skill it belongs to and how many questions it needs. */
    public record Need(String taskTypeKey, String skill, int count) {
    }

    /** An exam-ready question; {@code level} is null when it has none. */
    public record Candidate(UUID publicId, CefrLevel level) {
    }

    /** The questions drawn per task type (fewer than needed when the bank ran out) and how they spread. */
    public record Picked(Map<String, List<UUID>> byTaskType, CefrReport report) {
    }

    public static Picked pick(List<Need> needs, Map<String, List<Candidate>> pool,
            Map<CefrLevel, Integer> targetPercents, long seed) {
        List<CefrLevel> allowed = allowedFallbackLevels(targetPercents);
        Map<String, Map<CefrLevel, Deque<UUID>>> byLevel = new LinkedHashMap<>();
        Map<String, Deque<UUID>> unclassified = new LinkedHashMap<>();
        for (Need need : needs) {
            splitByLevel(need.taskTypeKey(), pool.getOrDefault(need.taskTypeKey(), List.of()), seed, byLevel,
                    unclassified);
        }
        Map<String, List<UUID>> picked = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> quotas = new TreeMap<>();
        Map<String, Map<String, Integer>> actuals = new TreeMap<>();
        List<CefrReport.Fallback> fallbacks = new ArrayList<>();
        int unclassifiedUsed = 0;
        for (String skill : skillsInOrder(needs)) {
            List<Need> skillNeeds = needs.stream().filter(need -> need.skill().equals(skill)).toList();
            Map<CefrLevel, Integer> quota = quotas(skillNeeds.stream().mapToInt(Need::count).sum(), targetPercents);
            quotas.put(skill, named(quota));
            Map<String, Integer> actual = new TreeMap<>();
            actuals.put(skill, actual);
            List<CefrLevel> tieOrder = shuffledLevels(seed ^ skill.hashCode());
            for (Need need : skillNeeds) {
                List<UUID> chosen = picked.computeIfAbsent(need.taskTypeKey(), key -> new ArrayList<>());
                for (int index = 0; index < need.count(); index++) {
                    Integer used = takeOne(need.taskTypeKey(), quota, tieOrder, allowed, byLevel, unclassified,
                            chosen, fallbacks, actual);
                    if (used == null) {
                        break;
                    }
                    unclassifiedUsed += used;
                }
            }
        }
        return new Picked(picked, new CefrReport(named(targetPercents), quotas, actuals, fallbacks,
                unclassifiedUsed));
    }

    // Takes one question for a task type; returns 1 when it had no level, 0 otherwise, null when none was left.
    private static Integer takeOne(String taskType, Map<CefrLevel, Integer> quota, List<CefrLevel> tieOrder,
            List<CefrLevel> allowed, Map<String, Map<CefrLevel, Deque<UUID>>> byLevel,
            Map<String, Deque<UUID>> unclassified, List<UUID> chosen, List<CefrReport.Fallback> fallbacks,
            Map<String, Integer> actual) {
        Map<CefrLevel, Deque<UUID>> levels = byLevel.get(taskType);
        List<CefrLevel> due = tieOrder.stream().filter(level -> quota.getOrDefault(level, 0) > 0)
                .sorted((first, second) -> Integer.compare(quota.get(second), quota.get(first))).toList();
        CefrLevel wanted = due.stream().filter(level -> !levels.get(level).isEmpty()).findFirst()
                .orElse(due.isEmpty() ? null : due.get(0));
        CefrLevel taken = wanted;
        if (wanted == null || levels.get(wanted).isEmpty()) {
            taken = nearestWithQuestions(wanted, allowed, levels);
        }
        UUID question = null;
        String usedName = null;
        if (taken != null) {
            question = levels.get(taken).poll();
            usedName = taken.name();
        } else if (!unclassified.get(taskType).isEmpty()) {
            question = unclassified.get(taskType).poll();
            usedName = CefrReport.UNCLASSIFIED;
        }
        if (question == null) {
            return null;
        }
        chosen.add(question);
        actual.merge(usedName, 1, Integer::sum);
        if (wanted != null) {
            quota.merge(wanted, -1, Integer::sum);
            if (!wanted.name().equals(usedName)) {
                fallbacks.add(new CefrReport.Fallback(taskType, wanted.name(), usedName));
            }
        }
        return CefrReport.UNCLASSIFIED.equals(usedName) ? 1 : 0;
    }

    private static CefrLevel nearestWithQuestions(CefrLevel wanted, List<CefrLevel> allowed,
            Map<CefrLevel, Deque<UUID>> levels) {
        CefrLevel origin = wanted == null ? CefrLevel.B2 : wanted;
        return allowed.stream().filter(level -> !levels.get(level).isEmpty())
                .min((first, second) -> {
                    int byDistance = Integer.compare(Math.abs(first.ordinal() - origin.ordinal()),
                            Math.abs(second.ordinal() - origin.ordinal()));
                    return byDistance != 0 ? byDistance : Integer.compare(first.ordinal(), second.ordinal());
                }).orElse(null);
    }

    /** Levels a stand-in may come from: B1 to C2 when the mix stays there, otherwise every level. */
    private static List<CefrLevel> allowedFallbackLevels(Map<CefrLevel, Integer> targetPercents) {
        boolean standardRange = targetPercents.keySet().stream().allMatch(level -> level.ordinal() >= CefrLevel.B1.ordinal());
        return List.of(CefrLevel.values()).stream()
                .filter(level -> !standardRange || level.ordinal() >= CefrLevel.B1.ordinal()).toList();
    }

    /** Largest-remainder rounding: each level's share of {@code total}, summing to exactly {@code total}. */
    static Map<CefrLevel, Integer> quotas(int total, Map<CefrLevel, Integer> targetPercents) {
        Map<CefrLevel, Integer> quota = new EnumMap<>(CefrLevel.class);
        List<CefrLevel> byRemainder = new ArrayList<>(targetPercents.keySet());
        int assigned = 0;
        for (CefrLevel level : byRemainder) {
            int whole = total * targetPercents.get(level) / PERCENT_TOTAL;
            quota.put(level, whole);
            assigned += whole;
        }
        byRemainder.sort((first, second) -> {
            int remainderFirst = total * targetPercents.get(first) % PERCENT_TOTAL;
            int remainderSecond = total * targetPercents.get(second) % PERCENT_TOTAL;
            return remainderFirst != remainderSecond ? Integer.compare(remainderSecond, remainderFirst)
                    : Integer.compare(first.ordinal(), second.ordinal());
        });
        for (int index = 0; assigned < total && !byRemainder.isEmpty(); index++, assigned++) {
            quota.merge(byRemainder.get(index % byRemainder.size()), 1, Integer::sum);
        }
        return quota;
    }

    private static void splitByLevel(String taskType, List<Candidate> candidates, long seed,
            Map<String, Map<CefrLevel, Deque<UUID>>> byLevel, Map<String, Deque<UUID>> unclassified) {
        Map<CefrLevel, Deque<UUID>> levels = new EnumMap<>(CefrLevel.class);
        for (CefrLevel level : CefrLevel.values()) {
            List<UUID> ids = new ArrayList<>(candidates.stream()
                    .filter(candidate -> candidate.level() == level).map(Candidate::publicId).toList());
            Collections.shuffle(ids, Seeds.random(seed ^ taskType.hashCode() ^ (level.ordinal() * 31L)));
            levels.put(level, new ArrayDeque<>(ids));
        }
        List<UUID> none = new ArrayList<>(candidates.stream().filter(candidate -> candidate.level() == null)
                .map(Candidate::publicId).toList());
        Collections.shuffle(none, Seeds.random(seed ^ taskType.hashCode()));
        byLevel.put(taskType, levels);
        unclassified.put(taskType, new ArrayDeque<>(none));
    }

    private static List<String> skillsInOrder(List<Need> needs) {
        return needs.stream().map(Need::skill).distinct().toList();
    }

    private static List<CefrLevel> shuffledLevels(long seed) {
        List<CefrLevel> levels = new ArrayList<>(List.of(CefrLevel.values()));
        Collections.shuffle(levels, Seeds.random(seed));
        return levels;
    }

    private static Map<String, Integer> named(Map<CefrLevel, Integer> byLevel) {
        Map<String, Integer> named = new TreeMap<>();
        byLevel.forEach((level, value) -> named.put(level.name(), value));
        return named;
    }
}
