package com.pte.assessment.internal.service.assembly;

import java.util.List;
import java.util.Map;

/**
 * How an exam's questions were spread over CEFR levels: the mix aimed for, what each skill was due by level,
 * what it got, and every place a question of another level had to stand in. Levels are named by their letter
 * code, with {@code UNCLASSIFIED} for questions that have no level yet.
 */
public record CefrReport(
        Map<String, Integer> target,
        Map<String, Map<String, Integer>> quotaBySkill,
        Map<String, Map<String, Integer>> actualBySkill,
        List<Fallback> fallbacks,
        int unclassifiedUsed) {

    public static final String UNCLASSIFIED = "UNCLASSIFIED";

    /** A question of level {@code used} taken where a question of level {@code wanted} was due. */
    public record Fallback(String taskTypeKey, String wanted, String used) {
    }
}
