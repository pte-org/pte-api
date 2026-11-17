package com.pte.itembank.internal.generation.review;

import com.pte.itembank.domain.enums.CefrLevel;
import com.pte.itembank.internal.generation.constant.GenerationLiveConstants;
import com.pte.itembank.internal.generation.live.GenerationLiveContext;
import com.pte.itembank.internal.generation.live.LiveEvent;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Tells the live view each step the reviewer goes through: its prompt, its call and the reading of its verdict. */
final class ReviewChecks {

    private ReviewChecks() {
    }

    /** The review prompt is put together: both prompts, the task type, the level, the hints and any earlier fix. */
    static void prompt(String taskTypeKey, CefrLevel cefrLevel, String systemPrompt, String userPrompt,
                       boolean withHints, boolean withFix) {
        report(GenerationLiveConstants.CHECK_REVIEW_PROMPT, GenerationLiveConstants.CHECK_PASSED, List.of(
                count(GenerationLiveConstants.DETAIL_SYSTEM_PROMPT, systemPrompt.length()),
                count(GenerationLiveConstants.DETAIL_TASK_PROMPT, userPrompt.length()),
                text(GenerationLiveConstants.DETAIL_TASK_TYPE, taskTypeKey),
                text(GenerationLiveConstants.DETAIL_CEFR, String.valueOf(cefrLevel)),
                text(GenerationLiveConstants.DETAIL_WITH_HINTS, String.valueOf(withHints)),
                text(GenerationLiveConstants.DETAIL_WITH_FIX, String.valueOf(withFix))));
    }

    static void reviewing() {
        report(GenerationLiveConstants.CHECK_REVIEW_WRITE, GenerationLiveConstants.CHECK_RUNNING, List.of());
    }

    static void reviewed(String output) {
        report(GenerationLiveConstants.CHECK_REVIEW_WRITE, GenerationLiveConstants.CHECK_PASSED,
                List.of(count(GenerationLiveConstants.DETAIL_OUTPUT, output == null ? 0 : output.length())));
    }

    /** The verdict was read: how many checks the reviewer made and how many of them failed. */
    static void verdict(ReviewVerdict verdict) {
        long failed = verdict.checks().values().stream().filter(check -> !check.ok()).count();
        // The failed criteria with the reviewer's note, and its fix: the reason the draft is written again.
        String failedChecks = verdict.checks().entrySet().stream().filter(entry -> !entry.getValue().ok())
                .map(entry -> entry.getKey() + "=" + entry.getValue().note()
                        + (entry.getValue().estimated() == null ? "" : " (estimated " + entry.getValue().estimated() + ")"))
                .collect(Collectors.joining(" | "));
        report(GenerationLiveConstants.CHECK_REVIEW_VERDICT, verdict.isPassed()
                        ? GenerationLiveConstants.CHECK_PASSED : GenerationLiveConstants.CHECK_FAILED,
                Map.of(GenerationLiveConstants.FACT_FAILED_CHECKS, failedChecks,
                        GenerationLiveConstants.FACT_FIX, String.valueOf(verdict.fixSuggestion())),
                List.of(new LiveEvent.CheckDetail(GenerationLiveConstants.DETAIL_CRITERIA,
                                GenerationLiveConstants.UNIT_ITEMS, null, verdict.checks().size()),
                        new LiveEvent.CheckDetail(GenerationLiveConstants.DETAIL_CRITERIA_FAILED,
                                GenerationLiveConstants.UNIT_ITEMS, null, (int) failed)));
    }

    /** The reviewer's answer could not be read as a verdict. */
    static void unreadable() {
        report(GenerationLiveConstants.CHECK_REVIEW_VERDICT, GenerationLiveConstants.CHECK_FAILED, Map.of(), List.of());
    }

    private static LiveEvent.CheckDetail count(String key, int value) {
        return new LiveEvent.CheckDetail(key, GenerationLiveConstants.UNIT_CHARS, null, value);
    }

    private static LiveEvent.CheckDetail text(String key, String value) {
        return new LiveEvent.CheckDetail(key, GenerationLiveConstants.UNIT_TEXT, value, null);
    }

    private static void report(String step, String status, List<LiveEvent.CheckDetail> details) {
        report(step, status, Map.of(), details);
    }

    private static void report(String step, String status, Map<String, String> facts,
                               List<LiveEvent.CheckDetail> details) {
        GenerationLiveContext.check(step, status, facts, details);
    }
}
