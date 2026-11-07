package com.pte.itembank.internal.generation.media;

import com.pte.itembank.internal.generation.draft.ItemDraft;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.CategoryChart;
import org.knowm.xchart.CategoryChartBuilder;
import org.knowm.xchart.PieChart;
import org.knowm.xchart.PieChartBuilder;
import org.knowm.xchart.internal.chartpart.Chart;
import org.knowm.xchart.CategorySeries;
import org.knowm.xchart.style.AxesChartStyler;
import org.knowm.xchart.style.Styler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders chart drafts to PNG images using XChart.
 * Supports BAR, LINE, PIE, and TABLE visualizations at 800×500 with neutral theme.
 * Runs headless via java.awt.headless=true.
 */
@Component
public class ChartRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChartRenderer.class);
    private static final int CHART_WIDTH = 800;
    private static final int CHART_HEIGHT = 500;
    private static final int MIN_FONT_SIZE = 14;

    public byte[] render(ItemDraft.ChartDraft draft) {
        System.setProperty("java.awt.headless", "true");

        try {
            String chartType = draft.chartType() != null ? draft.chartType().toUpperCase() : "BAR";

            byte[] imageBytes = switch (chartType) {
                case "BAR" -> renderBarChart(draft);
                case "LINE" -> renderLineChart(draft);
                case "PIE" -> renderPieChart(draft);
                case "TABLE" -> renderTableChart(draft);
                default -> throw new IllegalArgumentException("Unsupported chart type: " + chartType);
            };

            return imageBytes;
        } catch (Exception e) {
            LOGGER.error("Failed to render chart", e);
            throw new RuntimeException("Chart rendering failed", e);
        }
    }

    private byte[] renderBarChart(ItemDraft.ChartDraft draft) throws Exception {
        CategoryChart chart = new CategoryChartBuilder()
                .width(CHART_WIDTH)
                .height(CHART_HEIGHT)
                .title(draft.title())
                .xAxisTitle(draft.xLabel())
                .yAxisTitle(draft.yLabel())
                .build();

        styleChart(chart.getStyler());
        styleAxes(chart.getStyler());

        // Add series
        if (draft.series() != null) {
            for (ItemDraft.ChartDraft.Series series : draft.series()) {
                List<Double> values = series.values() != null ? new ArrayList<>(series.values()) : new ArrayList<>();
                chart.addSeries(series.name(), draft.categories(), values);
            }
        }

        return chartToBytes(chart);
    }

    private byte[] renderLineChart(ItemDraft.ChartDraft draft) throws Exception {
        CategoryChart chart = new CategoryChartBuilder()
                .width(CHART_WIDTH)
                .height(CHART_HEIGHT)
                .title(draft.title())
                .xAxisTitle(draft.xLabel())
                .yAxisTitle(draft.yLabel())
                .build();

        styleChart(chart.getStyler());
        styleAxes(chart.getStyler());
        chart.getStyler().setDefaultSeriesRenderStyle(CategorySeries.CategorySeriesRenderStyle.Line);

        // Add series
        if (draft.series() != null) {
            for (ItemDraft.ChartDraft.Series series : draft.series()) {
                List<Double> values = series.values() != null ? new ArrayList<>(series.values()) : new ArrayList<>();
                chart.addSeries(series.name(), draft.categories(), values);
            }
        }

        return chartToBytes(chart);
    }

    private byte[] renderPieChart(ItemDraft.ChartDraft draft) throws Exception {
        PieChart chart = new PieChartBuilder()
                .width(CHART_WIDTH)
                .height(CHART_HEIGHT)
                .title(draft.title())
                .build();

        styleChart(chart.getStyler());

        // Add single series
        if (draft.series() != null && !draft.series().isEmpty()) {
            ItemDraft.ChartDraft.Series series = draft.series().get(0);
            for (int i = 0; i < draft.categories().size(); i++) {
                String category = draft.categories().get(i);
                double value = series.values() != null && i < series.values().size() ? series.values().get(i) : 0;
                chart.addSeries(category, value);
            }
        }

        return chartToBytes(chart);
    }

    private byte[] renderTableChart(ItemDraft.ChartDraft draft) throws Exception {
        // Create a simple image-based table using BufferedImage and Graphics2D
        int cellWidth = 80;
        int cellHeight = 30;
        int cols = 1 + (draft.series() != null ? draft.series().size() : 0); // +1 for row headers
        int rows = 1 + draft.categories().size(); // +1 for column headers
        int width = cols * cellWidth;
        int height = rows * cellHeight;

        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
                width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        // Set rendering hints for quality
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Fill background
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, width, height);

        // Set font
        g2d.setFont(new Font("Arial", Font.PLAIN, 12));
        g2d.setColor(Color.BLACK);

        // Draw table
        // Column headers
        for (int col = 0; col < cols; col++) {
            String header = col == 0 ? draft.title() : (draft.series() != null && col - 1 < draft.series().size() ? draft.series().get(col - 1).name() : "");
            drawCell(g2d, header, col * cellWidth, 0, cellWidth, cellHeight);
        }

        // Row data
        for (int row = 0; row < draft.categories().size(); row++) {
            String catName = draft.categories().get(row);
            drawCell(g2d, catName, 0, (row + 1) * cellHeight, cellWidth, cellHeight);

            if (draft.series() != null) {
                for (int col = 0; col < draft.series().size(); col++) {
                    ItemDraft.ChartDraft.Series series = draft.series().get(col);
                    String value = series.values() != null && row < series.values().size() ? String.format("%.1f", series.values().get(row)) : "";
                    drawCell(g2d, value, (col + 1) * cellWidth, (row + 1) * cellHeight, cellWidth, cellHeight);
                }
            }
        }

        g2d.dispose();

        // Convert to PNG bytes
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }

    private void drawCell(Graphics2D g2d, String text, int x, int y, int width, int height) {
        // Draw border
        g2d.setColor(Color.BLACK);
        g2d.drawRect(x, y, width, height);

        // Draw text
        g2d.setColor(Color.BLACK);
        if (text != null && !text.isEmpty()) {
            FontMetrics fm = g2d.getFontMetrics();
            int textX = x + (width - fm.stringWidth(text)) / 2;
            int textY = y + ((height - fm.getHeight()) / 2) + fm.getAscent();
            g2d.drawString(text, textX, textY);
        }
    }

    private void styleAxes(AxesChartStyler styler) {
        styler.setAxisTitleFont(new Font("Arial", Font.BOLD, MIN_FONT_SIZE));
        styler.setAxisTickLabelsFont(new Font("Arial", Font.PLAIN, MIN_FONT_SIZE));
    }

    private void styleChart(Styler styler) {
        styler.setChartTitleFont(new Font("Arial", Font.BOLD, 16));
        styler.setLegendFont(new Font("Arial", Font.PLAIN, MIN_FONT_SIZE));

        // Neutral color scheme
        styler.setPlotBackgroundColor(Color.WHITE);
        styler.setChartBackgroundColor(Color.WHITE);
        styler.setLegendBackgroundColor(Color.WHITE);
    }

    private byte[] chartToBytes(Chart<?, ?> chart) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        BitmapEncoder.saveBitmap(chart, baos, BitmapEncoder.BitmapFormat.PNG);
        return baos.toByteArray();
    }
}
