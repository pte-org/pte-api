package com.pte.billing;

/**
 * The AI question generation limits a platform admin edits on the AI overview page, each stored as a platform setting.
 * The range is what the settings page accepts; the default is the deployment value used until then.
 */
public enum QuestionGenerationLimit {

    ITEMS_PER_JOB("question_generation_max_items_per_job", 10, 1, 50, "questions",
            "Questions one generation job may ask for"),
    ITEMS_PER_USER_PER_DAY("question_generation_max_items_per_user_per_day", 100, 1, 10_000, "questions",
            "Questions one admin may generate per day"),
    ITEMS_PER_DAY("question_generation_max_items_per_day", 300, 1, 100_000, "questions",
            "Questions all admins together may generate per day"),
    CONCURRENT_JOBS("question_generation_max_concurrent_jobs", 3, 1, 20, "jobs",
            "Generation jobs that may run at the same time"),
    /** One write-review round costs up to five calls, so fewer would allow no round at all. */
    CALLS_PER_ITEM("question_generation_max_calls_per_item", 40, 5, 200, "calls",
            "AI calls one generated question may use (writing, review, media)");

    private final String key;
    private final int defaultValue;
    private final int minimum;
    private final int maximum;
    private final String unit;
    private final String description;

    QuestionGenerationLimit(String key, int defaultValue, int minimum, int maximum, String unit, String description) {
        this.key = key;
        this.defaultValue = defaultValue;
        this.minimum = minimum;
        this.maximum = maximum;
        this.unit = unit;
        this.description = description;
    }

    public String key() {
        return key;
    }

    public int defaultValue() {
        return defaultValue;
    }

    public int minimum() {
        return minimum;
    }

    public int maximum() {
        return maximum;
    }

    public String unit() {
        return unit;
    }

    public String description() {
        return description;
    }
}
