package com.pte.chat.promptsuite;

import java.util.ArrayList;
import java.util.List;

/** What one live call returned and how it scored; {@code error} is set when the call produced no usable answer. */
public record PromptSuiteRun(int run, Boolean onTopic, String finishReason, int tokensIn, int tokensOut,
                             int tokensThinking, long latencyMs, List<PromptSuiteRules.RuleResult> rules,
                             String answer, String error) {

    public static final String FINISH_MAX_TOKENS = "MAX_TOKENS";

    public boolean hitMaxTokens() {
        return FINISH_MAX_TOKENS.equals(finishReason);
    }

    /** Why this run fails the case, empty when it passes. Order: errors, token cap, topic mismatch, rules. */
    public List<String> failures(boolean expectedOnTopic) {
        List<String> failures = new ArrayList<>();
        if (error != null) {
            failures.add("error: " + error);
        }
        if (hitMaxTokens()) {
            failures.add("finishReason MAX_TOKENS");
        }
        if (error == null && !Boolean.valueOf(expectedOnTopic).equals(onTopic)) {
            failures.add("is_on_topic " + onTopic + ", expected " + expectedOnTopic);
        }
        for (PromptSuiteRules.RuleResult rule : rules) {
            if (!rule.passed()) {
                failures.add("rule " + rule.rule() + ": " + rule.detail());
            }
        }
        return failures;
    }

    public int outputPlusThinking() {
        return tokensOut + tokensThinking;
    }

    /** All runs of one case; the case passes only if every run passes. */
    public record CaseResult(PromptSuiteCase testCase, List<PromptSuiteRun> runs) {

        public boolean passed() {
            return !runs.isEmpty()
                    && runs.stream().allMatch(run -> run.failures(testCase.expectedOnTopic()).isEmpty());
        }

        public List<String> failures() {
            List<String> all = new ArrayList<>();
            for (PromptSuiteRun run : runs) {
                for (String failure : run.failures(testCase.expectedOnTopic())) {
                    all.add(testCase.id() + " run " + run.run() + ": " + failure);
                }
            }
            return all;
        }
    }
}
