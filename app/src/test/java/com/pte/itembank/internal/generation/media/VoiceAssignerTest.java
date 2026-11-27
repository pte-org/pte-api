package com.pte.itembank.internal.generation.media;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoiceAssignerTest {

    private static VoiceAssigner assigner(String disabled) {
        return new VoiceAssigner(EnabledVoicesTest.enabledWith(disabled, new MockEnvironment()));
    }

    private static List<String> batchOf(VoiceAssigner assigner, long seed, int size) {
        List<String> voices = new ArrayList<>();
        for (int position = 0; position < size; position++) {
            voices.add(assigner.assign(seed, position));
        }
        return voices;
    }

    private static boolean isUk(String voice) {
        return voice.startsWith("b");
    }

    private static boolean isFemale(String voice) {
        return voice.charAt(1) == 'f';
    }

    @Test
    void aBatchOfTenBalancesAccentsAndGendersAndNeverRepeatsANeighbour() {
        var assigner = assigner("");
        for (long seed = 1; seed <= 50; seed++) {
            List<String> voices = batchOf(assigner, seed * 104729L, 10);
            long uk = voices.stream().filter(VoiceAssignerTest::isUk).count();
            long women = voices.stream().filter(VoiceAssignerTest::isFemale).count();
            assertEquals(5, uk, "UK voices with seed " + seed);
            assertTrue(women >= 4 && women <= 6, "women " + women + " with seed " + seed);
            for (int i = 1; i < voices.size(); i++) {
                assertNotEquals(voices.get(i - 1), voices.get(i), "neighbours with seed " + seed);
            }
        }
    }

    @Test
    void theSameSeedAndPositionAlwaysGiveTheSameVoice() {
        var assigner = assigner("");
        assertEquals(batchOf(assigner, 77L, 12), batchOf(assigner, 77L, 12));
    }

    @Test
    void differentSeedsGiveDifferentOrders() {
        var assigner = assigner("");
        long distinct = LongStream.range(0, 30).mapToObj(seed -> batchOf(assigner, seed * 7919L, 8)).distinct().count();
        assertTrue(distinct > 10, "only " + distinct + " different orders in 30 seeds");
    }

    @Test
    void anAccentSwitchedOffIsReplacedByTheOtherOne() {
        var assigner = assigner("bf_emma,bf_alice,bf_isabella,bf_lily,bm_george,bm_daniel,bm_fable,bm_lewis");
        for (String voice : batchOf(assigner, 5L, 10)) {
            assertEquals('a', voice.charAt(0));
        }
    }

    @Test
    void aGenderSwitchedOffInOneAccentFallsBackToTheOtherGenderWithoutRepeatingANeighbour() {
        var assigner = assigner("bm_george,bm_daniel,bm_fable,bm_lewis");
        for (long seed = 0; seed < 20; seed++) {
            List<String> voices = batchOf(assigner, seed, 10);
            for (int i = 1; i < voices.size(); i++) {
                assertNotEquals(voices.get(i - 1), voices.get(i));
            }
        }
    }

    @Test
    void aSwitchedOffVoiceIsNeverAssigned() {
        var assigner = assigner("af_heart,bf_emma");
        for (long seed = 0; seed < 30; seed++) {
            for (String voice : batchOf(assigner, seed, 16)) {
                assertNotEquals("af_heart", voice);
                assertNotEquals("bf_emma", voice);
            }
        }
    }
}
