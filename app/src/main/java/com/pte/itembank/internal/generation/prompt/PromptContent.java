package com.pte.itembank.internal.generation.prompt;

import com.pte.itembank.PromptDocument.Block;

import java.util.List;

/**
 * An editable prompt as it is stored: a title, the blocks before the first heading, and titled sections of blocks.
 * Nothing is locked; what a prompt may contain is checked when it is saved, not by hiding parts of it.
 *
 * @param intro the blocks that come before the first section (no heading)
 */
public record PromptContent(String title, List<Block> intro, List<Section> sections) {

    /** A titled group of blocks; {@code id} is a stable slug of the title. */
    public record Section(String id, String title, List<Block> blocks) {
    }
}
