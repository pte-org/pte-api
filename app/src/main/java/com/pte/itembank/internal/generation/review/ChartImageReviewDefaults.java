package com.pte.itembank.internal.generation.review;

import com.pte.itembank.PromptDocument.Block;
import com.pte.itembank.internal.generation.prompt.PromptContent;
import com.pte.itembank.internal.generation.prompt.PromptTextAssembler;

import java.util.List;

/**
 * The prompt of the image reviewer the application ships with: the first version stored in the database and the text
 * used when none is stored. The reviewer sees the drawn chart and the data it was drawn from, and says whether a
 * candidate could describe the picture from what it shows.
 */
public final class ChartImageReviewDefaults {

    private static final String TITLE = "Chart image reviewer";

    private ChartImageReviewDefaults() {
    }

    public static PromptContent content() {
        return new PromptContent(TITLE, List.of(Block.text("",
                "You review a chart image made for a PTE Academic Describe Image task. You receive the image and the "
                        + "data it was drawn from. Judge only the picture: the data itself was checked before.")),
                List.of(section("checks", "What to check", Block.list("", List.of(
                                "The chart type, the title and both axis labels match the data.",
                                "Every category and series in the data is visible, with its label, and none is extra.",
                                "Values read from the picture agree with the data within what the scale allows.",
                                "The legend names each series and its colours or patterns can be told apart.",
                                "No text overlaps, is cut off or is too small to read at normal size.",
                                "A candidate could describe the main trends and comparisons from the picture alone."))),
                        section("output", "Output", Block.text("",
                                        "Return one JSON object only. List each problem as one short sentence; "
                                                + "an empty list means the image passes."),
                                Block.fields("", List.of("verdict: PASS|FAIL", "issues: [\"...\"]")))));
    }

    /** The shipped prompt as the text the model receives. */
    public static String text() {
        return PromptTextAssembler.assembleWithoutTitle(content());
    }

    private static PromptContent.Section section(String id, String title, Block... blocks) {
        return new PromptContent.Section(id, title, List.of(blocks));
    }
}
