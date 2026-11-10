package com.pte.itembank;

import java.util.List;

/**
 * An AI prompt written as structured sections instead of one block of text, so the admin pages can show it as cards,
 * lists and level tables. The backend still assembles the text that is sent to the model ({@code assembledText}).
 * Shared vocabulary of the generation, review and scoring prompts; it lives in the module's public package because
 * the scoring module supplies its part through {@link ScoringPromptSource}.
 *
 * @param category      {@code GENERATION}, {@code REVIEW}, {@code SCORING} or {@code SHARED}
 * @param key           the task type, or the name of the shared block
 * @param assembledText the text the model receives, read-only; empty when it cannot be built without a real item
 */
public record PromptDocument(String category, String key, String title, List<Section> sections,
                             String assembledText) {

    /** How a block is drawn. */
    public enum BlockType {
        /** A paragraph. */
        TEXT,
        /** Bullet points. */
        LIST,
        /** A fixed-width example, such as a JSON reply. */
        CODE,
        /** The description of every score level of one trait. */
        LEVELS,
        /** The fields of a JSON reply, one "name: example value" row each; the text sent is the JSON they make. */
        FIELDS
    }

    /** One level of a trait's scale and what it means. */
    public record LevelRow(int level, String text) {
    }

    /**
     * One piece of a section.
     *
     * @param label   a small heading above the block (a trait name for {@code LEVELS}); may be empty
     * @param text    the content of a {@code TEXT} or {@code CODE} block
     * @param items   the entries of a {@code LIST} block
     * @param levels  the rows of a {@code LEVELS} block
     * @param sentToAi false for reference material the model never receives (a trait Azure or code scores)
     */
    public record Block(BlockType type, String label, String text, List<String> items, List<LevelRow> levels,
                        boolean sentToAi) {

        public static Block text(String label, String text) {
            return new Block(BlockType.TEXT, label, text, List.of(), List.of(), true);
        }

        public static Block list(String label, List<String> items) {
            return new Block(BlockType.LIST, label, "", items, List.of(), true);
        }

        public static Block code(String label, String text) {
            return new Block(BlockType.CODE, label, text, List.of(), List.of(), true);
        }

        public static Block fields(String label, List<String> rows) {
            return new Block(BlockType.FIELDS, label, "", rows, List.of(), true);
        }

        public static Block levels(String label, List<LevelRow> levels, boolean sentToAi) {
            return new Block(BlockType.LEVELS, label, "", List.of(), levels, sentToAi);
        }
    }

    /**
     * A titled group of blocks.
     *
     * @param locked true for wording that fixes what the validator accepts (the reply format); it is shown with a
     *               lock because it is not meant to be edited
     */
    public record Section(String id, String title, boolean locked, List<Block> blocks) {
    }

    /** What the list page shows for one prompt; {@code tags} are skills or the engines that do the scoring. */
    public record Summary(String category, String key, String title, List<String> tags) {
    }
}
