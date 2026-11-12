package com.pte.itembank.internal.generation.draft.image;

import com.pte.itembank.domain.enums.CefrLevel;
import com.pte.itembank.domain.enums.QuestionPool;
import com.pte.itembank.internal.generation.draft.DraftParser;
import com.pte.itembank.internal.generation.draft.ItemDraft;
import com.pte.itembank.internal.generation.exception.GenerationRequestInvalidException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PictureDraftsTest {

    private final DraftParser parser = new DraftParser(new JsonMapper());
    private static final String JSON = """
            {"taskType": "DESCRIBE_IMAGE", "topic": "Recycling", "title": "Four stages of recycling",
             "imagePrompt": "A clean flowchart of four labelled recycling stages on a white background",
             "keyPoints": ["Collection", "Sorting", "Washing", "Reuse"],
             "referenceDescription": "The picture shows how bottles are recycled in four stages."}
            """;

    @Test
    void thePictureVariantIsReadByItsOwnKey() {
        ItemDraft draft = parser.parse(JSON, "DESCRIBE_IMAGE_PICTURE");

        assertThat(draft).isInstanceOf(ItemDraft.PictureDraft.class);
        ItemDraft.PictureDraft picture = (ItemDraft.PictureDraft) draft;
        assertThat(picture.keyPoints()).hasSize(4);
        assertThat(PictureDrafts.validate(picture)).isEmpty();
    }

    @Test
    void aMissingImagePromptIsASchemaError() {
        assertThatThrownBy(() -> parser.parse(JSON.replace("imagePrompt", "image"), "DESCRIBE_IMAGE_PICTURE"))
                .isInstanceOf(GenerationRequestInvalidException.class);
    }

    @Test
    void tooFewKeyPointsAndAThinPromptAreReported() {
        var thin = new ItemDraft.PictureDraft("t", "t", "A cat", List.of("one"), "ref");

        assertThat(PictureDrafts.validate(thin)).extracting(issue -> issue.code())
                .containsExactly("imagePrompt", "keyPoints");
    }

    @Test
    void theQuestionIsADescribeImageWithTheModelAnswerAsReference() {
        var picture = (ItemDraft.PictureDraft) parser.parse(JSON, "DESCRIBE_IMAGE_PICTURE");

        var question = PictureDrafts.toQuestion(picture, CefrLevel.B2, QuestionPool.PRACTICE);

        assertThat(question.taskTypeKey()).isEqualTo("DESCRIBE_IMAGE");
        assertThat(question.referenceAnswerText()).startsWith("The picture shows").contains("Sorting");
    }
}
