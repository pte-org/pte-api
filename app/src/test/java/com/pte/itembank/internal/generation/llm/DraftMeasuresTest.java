package com.pte.itembank.internal.generation.llm;

import com.pte.itembank.internal.generation.constant.GenerationLiveConstants;
import com.pte.itembank.internal.generation.draft.AnswerHint;
import com.pte.itembank.internal.generation.draft.ItemDraft;
import com.pte.itembank.internal.generation.live.LiveEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DraftMeasuresTest {

    private final ItemDraft draft = new ItemDraft.ReorderDraft("City parks", List.of("One.", "Two.", "Three."));

    @Test
    void listsEveryFieldTheModelFilledInAndTheHints() {
        List<LiveEvent.CheckDetail> present = DraftMeasures.present(draft, new AnswerHint("Đọc kỹ", null));

        assertThat(present).extracting(LiveEvent.CheckDetail::key)
                .containsExactly("topic", "paragraphs", GenerationLiveConstants.DETAIL_HINT_VI);
    }

    @Test
    void measuresTextsInWordsAndListsInItems() {
        List<LiveEvent.CheckDetail> measures = DraftMeasures.measures(draft);

        assertThat(measures).containsExactly(
                new LiveEvent.CheckDetail("topic", GenerationLiveConstants.UNIT_WORDS, null, 2),
                new LiveEvent.CheckDetail("paragraphs", GenerationLiveConstants.UNIT_ITEMS, null, 3));
    }
}
