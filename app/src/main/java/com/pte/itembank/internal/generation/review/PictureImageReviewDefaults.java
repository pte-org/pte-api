package com.pte.itembank.internal.generation.review;

import com.pte.itembank.PromptDocument.Block;
import com.pte.itembank.internal.generation.prompt.PromptContent;
import com.pte.itembank.internal.generation.prompt.PromptTextAssembler;

import java.util.List;

/**
 * The shipped prompt of the reviewer that looks at a painted Describe Image picture next to what it was asked to show:
 * the first version stored in the database and the text used when none is stored.
 */
public final class PictureImageReviewDefaults {

    private static final String TITLE = "Picture image reviewer";

    private PictureImageReviewDefaults() {
    }

    public static PromptContent content() {
        return new PromptContent(TITLE, List.of(Block.text("",
                "You review a picture painted for a PTE Academic Describe Image task. You receive the image and what it "
                        + "was asked to show. Judge only the picture.")),
                List.of(section("checks", "What to check", Block.list("", List.of(
                                "Every key point can be seen in the picture.",
                                "Labels are real English words, spelled correctly, and readable at normal size.",
                                "There is no garbled text, extra unrelated text, watermark, logo or real person.",
                                "Nothing is unsafe or unsuitable for an exam.",
                                "A candidate could describe the main elements or stages in 40 seconds from the picture alone."))),
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
