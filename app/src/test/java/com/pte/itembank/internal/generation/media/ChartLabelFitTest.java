package com.pte.itembank.internal.generation.media;

import org.junit.jupiter.api.Test;
import org.knowm.xchart.CategoryChart;
import org.knowm.xchart.CategoryChartBuilder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChartLabelFitTest {

    @Test
    void shortLabelsStayFlatAndCrowdedOnesAreTurned() {
        CategoryChart few = new CategoryChartBuilder().width(800).height(500).build();
        ChartRenderer.fitCategoryLabels(few.getStyler(), List.of("2020", "2021", "2022"));
        assertThat(few.getStyler().getXAxisLabelRotation()).isZero();

        CategoryChart crowded = new CategoryChartBuilder().width(800).height(500).build();
        ChartRenderer.fitCategoryLabels(crowded.getStyler(), List.of("Renewable energy", "Natural gas", "Nuclear power",
                "Coal and lignite", "Oil products", "Imported electricity"));
        assertThat(crowded.getStyler().getXAxisLabelRotation()).isEqualTo(45);
    }
}
