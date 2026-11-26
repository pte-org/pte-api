package com.pte.itembank.internal.generation.media;

import com.pte.itembank.internal.generation.config.QuestionGenerationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests for VoicePicker. */
class VoicePickerTest {

    @Test
    void testPickAlternatesAccents() {
        var picker = pickerWithDisabled("");
        long seed = 12345L;

        boolean first = isUk(picker.pick(0, seed));
        assertNotEquals(first, isUk(picker.pick(1, seed)), "Neighbouring items should use different accents");
        assertEquals(first, isUk(picker.pick(2, seed)), "Every other item should share the accent");
    }

    @Test
    void firstItemsOfManyJobsUseBothAccents() {
        var picker = pickerWithDisabled("");
        int ukCount = 0;
        int jobs = 200;
        for (long seed = 0; seed < jobs; seed++) {
            if (isUk(picker.pick(0, seed * 7919L))) {
                ukCount++;
            }
        }
        // A batch runs one item per job, so its accents come only from the seed.
        assertTrue(ukCount > jobs / 4 && ukCount < jobs * 3 / 4, "UK voices picked: " + ukCount);
    }

    @Test
    void testPickDeterministicWithSeed() {
        var picker = pickerWithDisabled("");
        assertEquals(picker.pick(0, 100L), picker.pick(0, 100L), "Same index and seed should return same voice");
    }

    @Test
    void usesTheOtherAccentWhenOneIsSwitchedOff() {
        var picker = pickerWithDisabled("bf_emma,bf_alice,bf_isabella,bf_lily,bm_george,bm_daniel,bm_fable,bm_lewis");
        for (long seed = 0; seed < 20; seed++) {
            assertTrue(picker.pick(0, seed).startsWith("a"), "Only American voices are left");
        }
    }

    @Test
    void neverPicksADisabledVoice() {
        var picker = pickerWithDisabled("af_heart,am_michael");
        for (long seed = 0; seed < 200; seed++) {
            String voice = picker.pick((int) (seed % 3), seed);
            assertNotEquals("af_heart", voice);
            assertNotEquals("am_michael", voice);
        }
    }

    @Test
    void aJobOutsideAnyBatchPicksAsBefore() {
        var picker = pickerWithDisabled("");
        for (long seed = 0; seed < 30; seed++) {
            assertEquals(picker.pick(1, seed), picker.pick(1, seed, null));
        }
    }

    @Test
    void aBatchJobFollowsTheBatchSequenceNotItsOwnSeed() {
        var picker = pickerWithDisabled("");
        var assigner = new VoiceAssigner(EnabledVoicesTest.enabledWith("", new MockEnvironment()));
        var position = new BatchPosition(4242L, 6);
        for (int item = 0; item < 3; item++) {
            assertEquals(assigner.assign(4242L, 6 + item), picker.pick(item, 999L + item, position));
        }
    }

    private static boolean isUk(String voice) {
        return voice.startsWith("b");
    }

    static VoicePicker pickerWithDisabled(String disabled) {
        var enabled = EnabledVoicesTest.enabledWith(disabled, new MockEnvironment());
        return new VoicePicker(enabled, new VoiceAssigner(enabled));
    }

    static QuestionGenerationProperties propertiesWithDisabled(String disabled) {
        var properties = new QuestionGenerationProperties();
        properties.getKokoro().setVoicesDisabled(disabled);
        return properties;
    }
}
