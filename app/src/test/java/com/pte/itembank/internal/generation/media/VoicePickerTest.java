package com.pte.itembank.internal.generation.media;

import com.pte.itembank.internal.generation.config.QuestionGenerationProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for VoicePicker.
 */
class VoicePickerTest {

    @Test
    void testPickAlternatesAccents() {
        var picker = createPickerWithVoices("af_heart,af_bella", "bf_emma,bm_george");
        long seed = 12345L;

        String voice0 = picker.pick(0, seed); // Even index -> US
        String voice1 = picker.pick(1, seed); // Odd index -> UK
        String voice2 = picker.pick(2, seed); // Even index -> US

        assertTrue(voice0.equals("af_heart") || voice0.equals("af_bella"), "Voice 0 should be US");
        assertTrue(voice1.equals("bf_emma") || voice1.equals("bm_george"), "Voice 1 should be UK");
        assertTrue(voice2.equals("af_heart") || voice2.equals("af_bella"), "Voice 2 should be US");
    }

    @Test
    void testPickDeterministicWithSeed() {
        var picker = createPickerWithVoices("af_heart,af_bella,am_michael", "bf_emma,bm_george");
        long seed = 100L;

        String voice1a = picker.pick(0, seed);
        String voice1b = picker.pick(0, seed);

        assertEquals(voice1a, voice1b, "Same index and seed should return same voice");
    }

    @Test
    void testPickDifferentWithDifferentIndex() {
        var picker = createPickerWithVoices("af_heart,af_bella", "bf_emma,bm_george");
        long seed = 123L;

        String voice0 = picker.pick(0, seed);
        String voice1 = picker.pick(1, seed);

        assertNotEquals(voice0, voice1, "Different indices should likely return different voices (or different accents)");
    }

    @Test
    void testPickWithEmptyConfig() {
        var picker = createPickerWithVoices("", "");
        String voice = picker.pick(0, 123L);
        assertEquals("af_heart", voice, "Should return fallback voice when config is empty");
    }

    private VoicePicker createPickerWithVoices(String usVoices, String ukVoices) {
        var props = mock(QuestionGenerationProperties.class);
        var kokoroProps = mock(QuestionGenerationProperties.Kokoro.class);
        when(props.getKokoro()).thenReturn(kokoroProps);
        when(kokoroProps.getVoicesUs()).thenReturn(usVoices);
        when(kokoroProps.getVoicesUk()).thenReturn(ukVoices);
        return new VoicePicker(props);
    }
}
