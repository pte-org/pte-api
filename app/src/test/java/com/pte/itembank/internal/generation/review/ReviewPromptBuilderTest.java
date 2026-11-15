package com.pte.itembank.internal.generation.review;

import com.pte.itembank.GenerationRubricTraits;
import com.pte.itembank.domain.enums.CefrLevel;
import com.pte.itembank.internal.generation.draft.ItemDraft;
import com.pte.itembank.internal.generation.prompt.PromptTestRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class ReviewPromptBuilderTest {

    @Mock
    private GenerationRubricTraits rubricTraits;

    private ReviewPromptBuilder builder;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        builder = new ReviewPromptBuilder(rubricTraits, PromptTestRegistry.create());
    }

    @Test
    void buildSystemPrompt_IncludesTaskTypeKey() {
        when(rubricTraits.find("SUMMARIZE_WRITTEN_TEXT")).thenReturn(Optional.empty());
        String prompt = builder.buildSystemPrompt("SUMMARIZE_WRITTEN_TEXT");
        assertTrue(prompt.contains("SUMMARIZE_WRITTEN_TEXT"));
    }

    @Test
    void buildSystemPrompt_IncludesReviewChecklist() {
        when(rubricTraits.find("WRITE_ESSAY")).thenReturn(Optional.empty());
        String prompt = builder.buildSystemPrompt("WRITE_ESSAY");
        assertTrue(prompt.contains("taskFidelity"));
        assertTrue(prompt.contains("cefrFit"));
        assertTrue(prompt.contains("answerKey"));
        assertTrue(prompt.contains("scorability"));
        assertTrue(prompt.contains("clarity"));
        assertTrue(prompt.contains("safety"));
    }

    @Test
    void buildSystemPrompt_IncludesJsonSchema() {
        when(rubricTraits.find("MC_READING_SINGLE")).thenReturn(Optional.empty());
        String prompt = builder.buildSystemPrompt("MC_READING_SINGLE");
        assertTrue(prompt.contains("\"verdict\""));
        assertTrue(prompt.contains("\"checks\""));
        assertTrue(prompt.contains("\"fixSuggestion\""));
        assertTrue(prompt.contains("PASS|FAIL"));
    }

    @Test
    void buildSystemPrompt_WithRubricTraits_IncludesTraitDetails() {
        List<GenerationRubricTraits.LevelDescriptor> levels = List.of(
                new GenerationRubricTraits.LevelDescriptor(0, "No trait shown"),
                new GenerationRubricTraits.LevelDescriptor(1, "Minimal trait"),
                new GenerationRubricTraits.LevelDescriptor(2, "Basic trait"),
                new GenerationRubricTraits.LevelDescriptor(3, "Proficient trait"),
                new GenerationRubricTraits.LevelDescriptor(4, "Advanced trait"),
                new GenerationRubricTraits.LevelDescriptor(5, "Expert trait")
        );

        GenerationRubricTraits.TraitInfo trait = new GenerationRubricTraits.TraitInfo(
                "test-trait",
                "Content Relevance",
                5,
                levels
        );

        GenerationRubricTraits.TaskTraitInfo rubric = new GenerationRubricTraits.TaskTraitInfo(
                "WRITE_ESSAY",
                List.of(trait)
        );
        when(rubricTraits.find("WRITE_ESSAY")).thenReturn(Optional.of(rubric));

        String prompt = builder.buildSystemPrompt("WRITE_ESSAY");
        assertTrue(prompt.contains("Content Relevance"));
        assertTrue(prompt.contains("Expert trait"));
        assertTrue(prompt.contains("(0-5)"));
    }

    @Test
    void buildSystemPrompt_DoesNotContainGeneratorPrompt() {
        when(rubricTraits.find("SUMMARIZE_WRITTEN_TEXT")).thenReturn(Optional.empty());
        String prompt = builder.buildSystemPrompt("SUMMARIZE_WRITTEN_TEXT");
        assertFalse(prompt.contains("Generate"));
        assertFalse(prompt.contains("You are an expert test writer"));
    }

    @Test
    void buildUserPrompt_IncludesDraftInDelimiters() {
        ItemDraft.PassageDraft draft = new ItemDraft.PassageDraft(
                "urban farming",
                "City farming reduces emissions.",
                List.of("farming reduces emissions"),
                null
        );
        String prompt = builder.buildUserPrompt(draft, CefrLevel.B2, null);
        assertTrue(prompt.contains("<<< QUESTION DRAFT >>>"));
        assertTrue(prompt.contains("<<< END DRAFT >>>"));
    }

    @Test
    void buildUserPrompt_IncludesCefrLevel() {
        ItemDraft.ChoiceDraft draft = new ItemDraft.ChoiceDraft(
                "topic",
                "passage text",
                "question text",
                List.of("A", "B"),
                List.of(0)
        );
        String prompt = builder.buildUserPrompt(draft, CefrLevel.C1, null);
        assertTrue(prompt.contains("C1"));
    }

    @Test
    void buildUserPrompt_WithoutFixSuggestion_OmitsPreviousAttempt() {
        ItemDraft.PassageDraft draft = new ItemDraft.PassageDraft(
                "topic",
                "passage",
                null,
                "essay prompt"
        );
        String prompt = builder.buildUserPrompt(draft, CefrLevel.B2, null);
        assertFalse(prompt.contains("Previous attempt"));
    }

    @Test
    void buildUserPrompt_WithFixSuggestion_IncludesPreviousAttempt() {
        ItemDraft.PassageDraft draft = new ItemDraft.PassageDraft(
                "topic",
                "passage",
                null,
                "essay prompt"
        );
        String fixSuggestion = "Improve clarity in the question wording";
        String prompt = builder.buildUserPrompt(draft, CefrLevel.B2, fixSuggestion);
        assertTrue(prompt.contains("Previous attempt feedback"));
        assertTrue(prompt.contains(fixSuggestion));
    }

    @Test
    void buildUserPrompt_BlankFixSuggestion_OmitsPreviousAttempt() {
        ItemDraft.BlankFillDraft draft = new ItemDraft.BlankFillDraft(
                "topic",
                "The ___ is blue.",
                List.of(new ItemDraft.BlankFillDraft.Slot("sky", List.of("cloud")))
        );
        String prompt = builder.buildUserPrompt(draft, CefrLevel.B1, "");
        assertFalse(prompt.contains("Previous attempt"));
    }

    @Test
    void buildUserPrompt_IncludesDraftSerialized() {
        ItemDraft.ReorderDraft draft = new ItemDraft.ReorderDraft(
                "topic",
                List.of("First paragraph.", "Second paragraph.", "Third paragraph.")
        );
        String prompt = builder.buildUserPrompt(draft, CefrLevel.B2, null);
        assertTrue(prompt.contains("First paragraph"));
        assertTrue(prompt.contains("Second paragraph"));
        assertTrue(prompt.contains("Third paragraph"));
    }

    @Test
    void buildSystemPrompt_MultipleTraits_AllIncluded() {
        List<GenerationRubricTraits.LevelDescriptor> levels1 = List.of(
                new GenerationRubricTraits.LevelDescriptor(0, "None"),
                new GenerationRubricTraits.LevelDescriptor(1, "Some"),
                new GenerationRubricTraits.LevelDescriptor(2, "Good"),
                new GenerationRubricTraits.LevelDescriptor(3, "Very good"),
                new GenerationRubricTraits.LevelDescriptor(4, "Excellent")
        );

        List<GenerationRubricTraits.LevelDescriptor> levels2 = List.of(
                new GenerationRubricTraits.LevelDescriptor(0, "No clarity"),
                new GenerationRubricTraits.LevelDescriptor(1, "Minimal clarity"),
                new GenerationRubricTraits.LevelDescriptor(2, "Clear"),
                new GenerationRubricTraits.LevelDescriptor(3, "Very clear"),
                new GenerationRubricTraits.LevelDescriptor(4, "Exceptionally clear")
        );

        GenerationRubricTraits.TraitInfo trait1 = new GenerationRubricTraits.TraitInfo(
                "relevance",
                "Content Relevance",
                4,
                levels1
        );
        GenerationRubricTraits.TraitInfo trait2 = new GenerationRubricTraits.TraitInfo(
                "clarity",
                "Expression Clarity",
                4,
                levels2
        );

        GenerationRubricTraits.TaskTraitInfo rubric = new GenerationRubricTraits.TaskTraitInfo(
                "WRITE_ESSAY",
                List.of(trait1, trait2)
        );
        when(rubricTraits.find("WRITE_ESSAY")).thenReturn(Optional.of(rubric));

        String prompt = builder.buildSystemPrompt("WRITE_ESSAY");
        assertTrue(prompt.contains("Content Relevance"));
        assertTrue(prompt.contains("Expression Clarity"));
        assertTrue(prompt.contains("Excellent"));
        assertTrue(prompt.contains("Exceptionally clear"));
    }

    @Test
    void theShippedPromptCarriesNoTokensAndTheTaskContextComesInFrontOfIt() {
        when(rubricTraits.find("WRITE_ESSAY")).thenReturn(Optional.empty());
        String shipped = com.pte.itembank.internal.generation.review.ReviewPromptDefaults.text();

        String prompt = builder.render(shipped, "WRITE_ESSAY");

        assertFalse(shipped.contains("{taskType}") || shipped.contains("{rubricTraits}"));
        assertTrue(prompt.startsWith("## Task Type"));
        assertTrue(prompt.indexOf("WRITE_ESSAY") < prompt.indexOf("rigorous question reviewer"));
    }

    @Test
    void aPromptThatPlacesTheTokensItselfGetsNoExtraBlock() {
        when(rubricTraits.find("WRITE_ESSAY")).thenReturn(Optional.empty());

        String prompt = builder.render("Review a {taskType} question.", "WRITE_ESSAY");

        assertTrue(prompt.equals("Review a WRITE_ESSAY question."));
    }

    @Test
    void buildUserPrompt_WithHints_SendsThemAsData() {
        ItemDraft.PassageDraft draft = new ItemDraft.PassageDraft(
                "urban farming", "City farming reduces emissions.", List.of("farming reduces emissions"), null);
        String prompt = builder.buildUserPrompt(draft,
                new com.pte.itembank.internal.generation.draft.AnswerHint("Đọc to rõ", "Read clearly"),
                CefrLevel.B2, null);
        assertTrue(prompt.contains("<<< STUDENT HINTS >>>"));
        assertTrue(prompt.contains("Read clearly"));
    }

    @Test
    void buildSystemPrompt_SaysHowToJudgeTheStudentHints() {
        when(rubricTraits.find("MC_READING_SINGLE")).thenReturn(Optional.empty());
        String prompt = builder.buildSystemPrompt("MC_READING_SINGLE");
        assertTrue(prompt.contains("STUDENT HINTS"));
        assertTrue(prompt.contains("return FAIL and say how to rewrite the hints"));
    }

    @Test
    void buildUserPrompt_WithoutHints_OmitsTheHintBlock() {
        ItemDraft.PassageDraft draft = new ItemDraft.PassageDraft(
                "urban farming", "City farming reduces emissions.", List.of("farming reduces emissions"), null);
        assertFalse(builder.buildUserPrompt(draft, CefrLevel.B2, null).contains("STUDENT HINTS"));
    }
}
