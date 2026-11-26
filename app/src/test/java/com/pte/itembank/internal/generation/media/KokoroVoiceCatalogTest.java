package com.pte.itembank.internal.generation.media;

import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KokoroVoiceCatalogTest {

    private static long countOf(KokoroVoiceCatalog.Accent accent) {
        return KokoroVoiceCatalog.all().stream().filter(voice -> voice.accent() == accent).count();
    }

    @Test
    void hasEnoughVoicesOfBothAccents() {
        assertTrue(countOf(KokoroVoiceCatalog.Accent.US) >= 6);
        assertTrue(countOf(KokoroVoiceCatalog.Accent.UK) >= 4);
    }

    @Test
    void codesAreUniqueAndLabelled() {
        var codes = new HashSet<String>();
        for (var voice : KokoroVoiceCatalog.all()) {
            assertTrue(codes.add(voice.code()), "duplicate " + voice.code());
            assertFalse(voice.label().isBlank());
        }
        assertEquals(18, codes.size());
    }

    @Test
    void theCodePrefixAgreesWithAccentAndGender() {
        for (var voice : KokoroVoiceCatalog.all()) {
            assertEquals(voice.accent() == KokoroVoiceCatalog.Accent.US ? 'a' : 'b', voice.code().charAt(0));
            assertEquals(voice.gender() == KokoroVoiceCatalog.Gender.FEMALE ? 'f' : 'm', voice.code().charAt(1));
        }
    }

    @Test
    void findLooksAVoiceUpByCode() {
        assertTrue(KokoroVoiceCatalog.find("bm_george").isPresent());
        assertTrue(KokoroVoiceCatalog.find("zz_none").isEmpty());
    }
}
