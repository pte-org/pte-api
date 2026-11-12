package com.pte.itembank.internal.generation.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageKindTest {

    @Test
    void autoBecomesAChartWhenTheTopicAsksForNumbers() {
        assertThat(ImageKind.AUTO.resolve("kinh tế, việc làm IT hiện nay, dạng biểu đồ")).isEqualTo(ImageKind.CHART);
        assertThat(ImageKind.AUTO.resolve("Energy use by country as a table")).isEqualTo(ImageKind.CHART);
        assertThat(ImageKind.AUTO.resolve("A busy city market")).isEqualTo(ImageKind.PICTURE);
        assertThat(ImageKind.AUTO.resolve(null)).isEqualTo(ImageKind.PICTURE);
    }

    @Test
    void aChosenKindIsKeptAndAnUnknownOneIsRefused() {
        assertThat(ImageKind.CHART.resolve("A busy city market")).isEqualTo(ImageKind.CHART);
        assertThat(ImageKind.parse(null)).isEqualTo(ImageKind.AUTO);
        assertThat(ImageKind.parse(" picture ")).isEqualTo(ImageKind.PICTURE);
        assertThatThrownBy(() -> ImageKind.parse("video")).isInstanceOf(IllegalArgumentException.class);
    }
}
