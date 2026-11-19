package com.pte.itembank.internal.generation.batch;

import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import com.pte.itembank.internal.generation.domain.BatchRow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Splits batch rows into child jobs. Pure: no Spring, no database. A row's questions are spread over its topics as
 * evenly as possible (25 over three topics → 9/8/8), then each topic group is cut into jobs of at most
 * {@code perJob} questions. Run order ({@code seq}) is continuous across rows, in row order.
 */
public final class BatchSplitter {

    /** One child job to create. {@code topicHint} is null for a row without topics. */
    public record ChildPlan(int rowIndex, int seq, String topicHint, int count) {
    }

    private BatchSplitter() {
    }

    /**
     * @param perJobSetting the configured items per job; clamped to the V34 CHECK (1..10) of {@code requested_count}
     */
    public static List<ChildPlan> split(List<BatchRow> rows, int perJobSetting) {
        int perJob = Math.max(1, Math.min(perJobSetting, QuestionGenerationConstants.MAX_ITEMS_PER_JOB_HARD_LIMIT));
        List<ChildPlan> children = new ArrayList<>();
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            BatchRow row = rows.get(rowIndex);
            List<String> topics = row.topicHints().isEmpty()
                    ? Collections.singletonList(null) : row.topicHints();
            int base = row.count() / topics.size();
            int extra = row.count() % topics.size();
            for (int topic = 0; topic < topics.size(); topic++) {
                int remaining = base + (topic < extra ? 1 : 0);
                while (remaining > 0) {
                    int size = Math.min(perJob, remaining);
                    children.add(new ChildPlan(rowIndex, children.size(), topics.get(topic), size));
                    remaining -= size;
                }
            }
        }
        return children;
    }
}
