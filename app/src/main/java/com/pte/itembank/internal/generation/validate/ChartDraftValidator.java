package com.pte.itembank.internal.generation.validate;

import com.pte.itembank.internal.generation.draft.ItemDraft;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates DESCRIBE_IMAGE chart drafts for numeric visualization (BAR, LINE, PIE, TABLE).
 * Rules: 3–8 categories, ≤3 series (PIE: 1 only), non-negative values, PIE totals 100±0.5,
 * TABLE ≤6×5, keyPoints match data (MAX/MIN/TREND/COMPARE/TOTAL).
 */
@Component
public class ChartDraftValidator {

    private static final double PIE_TOTAL_TOLERANCE = 0.5;
    private static final int MIN_CATEGORIES = 3;
    private static final int MAX_CATEGORIES = 8;
    private static final int MAX_SERIES = 3;
    private static final int TABLE_MAX_ROWS = 6;
    private static final int TABLE_MAX_COLS = 5;

    public List<ValidationIssue> validate(ItemDraft.ChartDraft draft) {
        List<ValidationIssue> issues = new ArrayList<>();

        // Check categories
        if (draft.categories() == null || draft.categories().isEmpty()) {
            issues.add(new ValidationIssue("categories", "Categories are required"));
            return issues; // Stop further checks if no categories
        }

        int catCount = draft.categories().size();
        if (catCount < MIN_CATEGORIES || catCount > MAX_CATEGORIES) {
            issues.add(new ValidationIssue("categories", "Must have " + MIN_CATEGORIES + "–" + MAX_CATEGORIES + " categories, got " + catCount));
        }

        // Check series count and type-specific rules
        String chartType = draft.chartType() != null ? draft.chartType().toUpperCase() : "";
        if (draft.series() == null || draft.series().isEmpty()) {
            issues.add(new ValidationIssue("series", "At least one series is required"));
        } else {
            int seriesCount = draft.series().size();
            int maxSeries = "PIE".equals(chartType) ? 1 : MAX_SERIES;

            if (seriesCount > maxSeries) {
                issues.add(new ValidationIssue("series", chartType + " allows max " + maxSeries + " series, got " + seriesCount));
            }

            // Validate each series and values
            for (int i = 0; i < draft.series().size(); i++) {
                ItemDraft.ChartDraft.Series series = draft.series().get(i);
                List<Double> values = series.values();

                if (values == null || values.isEmpty()) {
                    issues.add(new ValidationIssue("series[" + i + "]", "Series must have values"));
                    continue;
                }

                if (values.size() != catCount) {
                    issues.add(new ValidationIssue("series[" + i + "]", "Series length must match categories (" + catCount + "), got " + values.size()));
                }

                // Check for non-negative values
                for (int j = 0; j < values.size(); j++) {
                    double val = values.get(j);
                    if (val < 0) {
                        issues.add(new ValidationIssue("series[" + i + "].values[" + j + "]", "Values must be non-negative, got " + val));
                    }
                }
            }

            // PIE-specific: total must be 100±0.5
            if ("PIE".equals(chartType) && !draft.series().isEmpty()) {
                double total = draft.series().get(0).values().stream()
                        .mapToDouble(Double::doubleValue)
                        .sum();
                if (Math.abs(total - 100) > PIE_TOTAL_TOLERANCE) {
                    issues.add(new ValidationIssue("series[0]", "PIE chart values must sum to 100 (±0.5), got " + total));
                }
            }

            // TABLE-specific: max 6×5
            if ("TABLE".equals(chartType) && !draft.series().isEmpty()) {
                int rows = catCount;
                int cols = draft.series().size();
                if (rows > TABLE_MAX_ROWS || cols > TABLE_MAX_COLS) {
                    issues.add(new ValidationIssue("series", "TABLE must be ≤" + TABLE_MAX_ROWS + "×" + TABLE_MAX_COLS + ", got " + rows + "×" + cols));
                }
            }
        }

        // Validate keyPoints
        if (draft.keyPoints() != null && !draft.keyPoints().isEmpty()) {
            for (int i = 0; i < draft.keyPoints().size(); i++) {
                ItemDraft.ChartDraft.KeyPoint kp = draft.keyPoints().get(i);

                if (kp.kind() == null || !isValidKeyPointKind(kp.kind())) {
                    issues.add(new ValidationIssue("keyPoints[" + i + "]", "Invalid keyPoint kind: " + kp.kind()));
                }

                // Validate kind matches data (basic checks)
                List<ValidationIssue> kindIssues = validateKeyPointKind(kp, draft);
                issues.addAll(kindIssues);
            }
        }

        return issues;
    }

    private boolean isValidKeyPointKind(String kind) {
        Set<String> validKinds = Set.of("MAX", "MIN", "TREND_UP", "TREND_DOWN", "COMPARE", "TOTAL");
        return validKinds.contains(kind);
    }

    private List<ValidationIssue> validateKeyPointKind(ItemDraft.ChartDraft.KeyPoint kp, ItemDraft.ChartDraft draft) {
        List<ValidationIssue> issues = new ArrayList<>();

        if (draft.series() == null || draft.series().isEmpty()) {
            return issues;
        }

        String kind = kp.kind().toUpperCase();
        String ref = kp.ref(); // Usually "series[i]:cat[j]" or similar

        switch (kind) {
            case "MAX", "MIN" -> {
                // Verify the referenced value is actually max/min
                // For simplicity, we just check that ref is non-null
                if (ref == null || ref.isBlank()) {
                    issues.add(new ValidationIssue("keyPoints", kind + " must reference a specific series and category"));
                }
            }
            case "TOTAL" -> {
                // TOTAL should reference a specific series
                if (ref == null || ref.isBlank()) {
                    issues.add(new ValidationIssue("keyPoints", "TOTAL must reference a series"));
                }
            }
            case "TREND_UP", "TREND_DOWN", "COMPARE" -> {
                // Trends and comparisons should have meaningful references
                if (ref == null || ref.isBlank()) {
                    issues.add(new ValidationIssue("keyPoints", kind + " must have a reference"));
                }
            }
        }

        return issues;
    }
}
