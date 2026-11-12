package com.pte.itembank.internal.generation.constant;

import java.util.List;

/** Error codes, messages and limits of editing AI prompts. */
public final class PromptVersionConstants {

    private PromptVersionConstants() {
    }

    public static final String API_BASE_PATH = "/api/v1/ai";
    public static final String CATEGORY_GENERATION = "GENERATION";
    public static final String CATEGORY_SHARED = "SHARED";
    public static final String CATEGORY_SCORING = "SCORING";
    public static final String CATEGORY_CHATBOT = "CHATBOT";
    /** The task type whose prompt is used to try out a changed shared block. */
    public static final String SHARED_TEST_TASK_TYPE = "MC_READING_SINGLE";
    public static final String TEST_CEFR_LEVEL = "B2";

    // The sample question a changed reviewer prompt is tried on
    public static final String SAMPLE_TOPIC = "Coral reefs";
    public static final String SAMPLE_PASSAGE = "Coral reefs support thousands of marine species, but rising ocean "
            + "temperatures and acidification are bleaching and weakening them across the world.";
    public static final String SAMPLE_QUESTION = "Which of the following is named as a threat to coral reefs?";
    public static final List<String> SAMPLE_OPTIONS = List.of("Rising ocean temperatures", "Growing fish populations",
            "Cooler currents", "Lower tides");
    public static final int SAMPLE_CORRECT_INDEX = 0;

    // Error codes (machine readable)
    public static final String VERSION_NOT_FOUND = "AI_PROMPT_VERSION_NOT_FOUND";
    public static final String CATEGORY_NOT_EDITABLE = "AI_PROMPT_CATEGORY_NOT_EDITABLE";
    public static final String NOT_EDITABLE = "AI_PROMPT_VERSION_NOT_EDITABLE";
    public static final String OPEN_VERSION_EXISTS = "AI_PROMPT_OPEN_VERSION_EXISTS";
    public static final String CONTENT_INVALID = "AI_PROMPT_CONTENT_INVALID";
    public static final String TEST_REQUIRED = "AI_PROMPT_TEST_REQUIRED";
    public static final String SELF_APPROVAL_FORBIDDEN = "AI_PROMPT_SELF_APPROVAL_FORBIDDEN";
    public static final String REASON_REQUIRED = "AI_PROMPT_REASON_REQUIRED";
    public static final String TRANSITION_INVALID = "AI_PROMPT_TRANSITION_INVALID";
    public static final String CONCURRENT_CHANGE = "AI_PROMPT_CONCURRENT_CHANGE";
    public static final String TEST_UNAVAILABLE = "AI_PROMPT_TEST_UNAVAILABLE";
    public static final String AUTHOR_ONLY = "AI_PROMPT_AUTHOR_ONLY";
    public static final String NO_CHANGE = "AI_PROMPT_NO_CHANGE";
    public static final String TEST_NOT_SUPPORTED = "AI_PROMPT_TEST_NOT_SUPPORTED";

    // Messages shown to the user
    public static final String VERSION_NOT_FOUND_MESSAGE = "Prompt version not found.";
    public static final String CATEGORY_NOT_EDITABLE_MESSAGE = "This kind of prompt cannot be edited yet.";
    public static final String NOT_EDITABLE_MESSAGE = "A proposal that is waiting for approval cannot be edited.";
    public static final String OPEN_VERSION_EXISTS_MESSAGE =
            "This prompt already has a proposal for changes.";
    public static final String TEST_REQUIRED_MESSAGE = "Run the prompt test and pass it before sending for approval.";
    public static final String SELF_APPROVAL_FORBIDDEN_MESSAGE = "You cannot approve your own change.";
    public static final String REASON_REQUIRED_MESSAGE = "A reason is required to reject a version.";
    public static final String TRANSITION_INVALID_MESSAGE = "This version cannot move to that status.";
    public static final String CONCURRENT_CHANGE_MESSAGE =
            "The prompt was changed by someone else. Reload it and try again.";
    public static final String TEST_UNAVAILABLE_MESSAGE = "The AI provider is not available, so the prompt cannot be tested.";
    public static final String TEST_NOT_SUPPORTED_MESSAGE =
            "Testing a scoring prompt is not available yet; an admin can apply the change without a test.";
    public static final String NO_CHANGE_MESSAGE = "Nothing changed compared with the version in use.";
    public static final String AUTHOR_ONLY_MESSAGE = "Only the author, a manager or an admin can change this version.";

    // Content rules
    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MAX_SECTION_ID_LENGTH = 64;
    public static final int MAX_INTRO_BLOCKS = 20;
    public static final int MAX_SECTIONS = 40;
    public static final int MAX_BLOCKS_PER_SECTION = 30;
    public static final int MAX_LIST_ITEMS = 60;
    public static final int MAX_BLOCK_TEXT_LENGTH = 20_000;
    public static final int MAX_ITEM_LENGTH = 2_000;
    public static final int MAX_ASSEMBLED_LENGTH = 60_000;
    public static final int MAX_REASON_LENGTH = 1_000;
    public static final int MAX_TEST_NOTE_LENGTH = 480;
    public static final int MAX_SAMPLE_LENGTH = 4_000;

    /** The tokens the generator fills in, all of which the shared block must keep. */
    public static final List<String> SHARED_REQUIRED_TOKENS =
            List.of("{cefr}", "{topicHint}", "{avoidTopics}", "{lengthRules}");

    /** Fields of the generator reply the parser requires, so the shared block must keep asking for them. */
    public static final List<String> SHARED_REQUIRED_PHRASES = List.of("answerHintVi", "answerHintEn");
    /** The data block the reviewer receives the student hints in, so the reviewer prompt must keep judging it. */
    public static final List<String> REVIEWER_REQUIRED_PHRASES = List.of("STUDENT HINTS");

    // Content problems
    public static final String PROBLEM_TITLE = "The title is required and must be at most 200 characters.";
    public static final String PROBLEM_EMPTY = "A prompt needs at least one block of text.";
    public static final String PROBLEM_TOO_MANY = "The prompt has too many sections or blocks.";
    public static final String PROBLEM_SECTION = "Every section needs a title and a unique id.";
    public static final String PROBLEM_BLOCK = "Every block needs a type and some content.";
    public static final String PROBLEM_FIELDS = "Every output row needs its own field name, written as name: example value.";
    public static final String PROBLEM_TOO_LONG = "A block or the whole prompt is too long.";
    public static final String PROBLEM_MISSING_TOKEN = "The shared block must keep the token ";
    public static final String PROBLEM_MISSING_PHRASE = "This prompt must keep its required instruction about ";
    public static final String PROBLEM_LOCKED_SECTION = "This section is used by the system and cannot be changed: ";
    public static final String PROBLEM_OUTPUT_FIELDS =
            "Only the example values of the reply can be changed; keep its text and field names in: ";
    public static final String PROBLEM_UNKNOWN_TOKEN = "A task prompt must not contain the token ";
}
