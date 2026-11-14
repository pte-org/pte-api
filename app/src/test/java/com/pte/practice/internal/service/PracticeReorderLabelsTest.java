package com.pte.practice.internal.service;

import com.pte.itembank.dto.response.PracticeQuestionContent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PracticeReorderLabelsTest {

    private static List<PracticeQuestionContent.Option> paragraphs(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> new PracticeQuestionContent.Option(UUID.randomUUID(), "P" + index, index, null))
                .toList();
    }

    @Test
    void paragraphsAreNeverShownInAnswerOrderAndLabelsAreTheShownPositions() {
        for (int seed = 0; seed < 200; seed++) {
            for (int count : new int[] {2, 3, 5}) {
                var shown = PracticeReorderLabels.shuffleAndRelabel(paragraphs(count), new Random(seed));
                assertThat(shown).extracting(option -> option.orderIndex())
                        .containsExactlyElementsOf(java.util.stream.IntStream.range(0, count).boxed().toList());
                assertThat(shown).extracting(option -> option.text())
                        .isNotEqualTo(paragraphs(count).stream().map(option -> option.text()).toList());
                assertThat(shown).extracting(option -> option.text()).containsExactlyInAnyOrderElementsOf(
                        paragraphs(count).stream().map(option -> option.text()).toList());
            }
        }
    }

    @Test
    void aSingleParagraphIsLeftAlone() {
        var one = paragraphs(1);
        assertThat(PracticeReorderLabels.shuffleAndRelabel(one, new Random(1))).isEqualTo(one);
    }
}
