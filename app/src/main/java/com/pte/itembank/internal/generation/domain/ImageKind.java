package com.pte.itembank.internal.generation.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * How a Describe Image question gets its picture: a chart drawn from generated data, or an illustration painted by
 * the image model. {@code AUTO} lets the topic decide: a topic that asks for numbers gets a chart.
 */
public enum ImageKind {
    AUTO,
    CHART,
    PICTURE;

    /** Words in a topic (English or Vietnamese) that ask for numbers rather than a scene. */
    private static final Pattern DATA_WORDS = Pattern.compile(
            "chart|graph|table|data|statistic|percent|figure|biểu đồ|bieu do|bảng|bang so lieu|số liệu|so lieu|thống kê");

    /** The kind actually made for a request: AUTO becomes CHART or PICTURE from the topic. */
    public ImageKind resolve(String topicHint) {
        if (this != AUTO) {
            return this;
        }
        return topicHint != null && DATA_WORDS.matcher(topicHint.toLowerCase(Locale.ROOT)).find() ? CHART : PICTURE;
    }

    /** The stored kind for a request value; a missing value is AUTO, an unknown one is refused by the caller. */
    public static ImageKind parse(String raw) {
        return raw == null || raw.isBlank() ? AUTO : ImageKind.valueOf(raw.strip().toUpperCase(Locale.ROOT));
    }
}
