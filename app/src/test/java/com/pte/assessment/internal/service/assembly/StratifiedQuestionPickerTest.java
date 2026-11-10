package com.pte.assessment.internal.service.assembly;

import static org.assertj.core.api.Assertions.assertThat;

import com.pte.assessment.internal.service.assembly.StratifiedQuestionPicker.Candidate;
import com.pte.assessment.internal.service.assembly.StratifiedQuestionPicker.Need;
import com.pte.assessment.internal.service.assembly.StratifiedQuestionPicker.Picked;
import com.pte.itembank.domain.enums.CefrLevel;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StratifiedQuestionPickerTest {

    private static Map<CefrLevel, Integer> evenStandardMix() {
        Map<CefrLevel, Integer> mix = new EnumMap<>(CefrLevel.class);
        mix.put(CefrLevel.B1, 25);
        mix.put(CefrLevel.B2, 25);
        mix.put(CefrLevel.C1, 25);
        mix.put(CefrLevel.C2, 25);
        return mix;
    }

    private static List<Candidate> bank(int perLevel, CefrLevel... levels) {
        List<Candidate> candidates = new ArrayList<>();
        for (CefrLevel level : levels) {
            for (int index = 0; index < perLevel; index++) {
                candidates.add(new Candidate(UUID.randomUUID(), level));
            }
        }
        return candidates;
    }

    private static Map<UUID, CefrLevel> levelsOf(Map<String, List<Candidate>> pool) {
        Map<UUID, CefrLevel> levels = new HashMap<>();
        pool.values().forEach(list -> list.forEach(candidate -> levels.put(candidate.publicId(), candidate.level())));
        return levels;
    }

    @Test
    void quotasAddUpToTheNumberOfQuestions() {
        Map<CefrLevel, Integer> quota = StratifiedQuestionPicker.quotas(7, evenStandardMix());

        assertThat(quota.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(7);
        assertThat(quota.values()).containsExactlyInAnyOrder(2, 2, 2, 1);
    }

    @Test
    void followsTheMixWhenTheBankIsDeepEnough() {
        Map<String, List<Candidate>> pool = new HashMap<>();
        pool.put("A", bank(10, CefrLevel.B1, CefrLevel.B2, CefrLevel.C1, CefrLevel.C2));
        pool.put("B", bank(10, CefrLevel.B1, CefrLevel.B2, CefrLevel.C1, CefrLevel.C2));
        List<Need> needs = List.of(new Need("A", "READING", 4), new Need("B", "READING", 4));

        Picked picked = StratifiedQuestionPicker.pick(needs, pool, evenStandardMix(), 1L);

        assertThat(picked.byTaskType().get("A")).hasSize(4);
        assertThat(picked.byTaskType().get("B")).hasSize(4);
        assertThat(picked.report().actualBySkill().get("READING"))
                .containsEntry("B1", 2).containsEntry("B2", 2).containsEntry("C1", 2).containsEntry("C2", 2);
        assertThat(picked.report().fallbacks()).isEmpty();
    }

    @Test
    void aMissingLevelIsStoodInForByTheNearestAndRecorded() {
        Map<String, List<Candidate>> pool = new HashMap<>();
        pool.put("A", bank(10, CefrLevel.B2, CefrLevel.C1, CefrLevel.C2));
        List<Need> needs = List.of(new Need("A", "READING", 4));

        Picked picked = StratifiedQuestionPicker.pick(needs, pool, evenStandardMix(), 1L);

        assertThat(picked.byTaskType().get("A")).hasSize(4);
        assertThat(picked.report().fallbacks()).extracting(CefrReport.Fallback::wanted).contains("B1");
        assertThat(picked.report().fallbacks()).extracting(CefrReport.Fallback::used).containsOnly("B2");
    }

    @Test
    void aStandardMixNeverReachesBelowB1() {
        Map<String, List<Candidate>> pool = new HashMap<>();
        pool.put("A", bank(10, CefrLevel.A1, CefrLevel.A2));
        List<Need> needs = List.of(new Need("A", "READING", 4));

        Picked picked = StratifiedQuestionPicker.pick(needs, pool, evenStandardMix(), 1L);

        assertThat(picked.byTaskType().get("A")).isEmpty();
    }

    @Test
    void unclassifiedQuestionsComeLastAndAreCounted() {
        Map<String, List<Candidate>> pool = new HashMap<>();
        pool.put("A", bank(10, (CefrLevel) null));
        List<Need> needs = List.of(new Need("A", "READING", 3));

        Picked picked = StratifiedQuestionPicker.pick(needs, pool, evenStandardMix(), 1L);

        assertThat(picked.byTaskType().get("A")).hasSize(3);
        assertThat(picked.report().unclassifiedUsed()).isEqualTo(3);
    }

    @Test
    void aCustomMixThatReachesBelowB1MayUseLowerLevels() {
        Map<CefrLevel, Integer> mix = new EnumMap<>(CefrLevel.class);
        mix.put(CefrLevel.A2, 50);
        mix.put(CefrLevel.B1, 50);
        Map<String, List<Candidate>> pool = new HashMap<>();
        pool.put("A", bank(10, CefrLevel.A2, CefrLevel.B1));

        Picked picked = StratifiedQuestionPicker.pick(List.of(new Need("A", "READING", 4)), pool, mix, 1L);

        assertThat(picked.report().actualBySkill().get("READING")).containsEntry("A2", 2).containsEntry("B1", 2);
    }

    @Test
    void theSameSeedDrawsTheSameQuestionsAndNoQuestionIsRepeated() {
        Map<String, List<Candidate>> pool = new HashMap<>();
        pool.put("A", bank(10, CefrLevel.B1, CefrLevel.B2, CefrLevel.C1, CefrLevel.C2));
        List<Need> needs = List.of(new Need("A", "READING", 8));

        Picked first = StratifiedQuestionPicker.pick(needs, pool, evenStandardMix(), 9L);
        Picked second = StratifiedQuestionPicker.pick(needs, pool, evenStandardMix(), 9L);

        assertThat(first.byTaskType()).isEqualTo(second.byTaskType());
        assertThat(first.byTaskType().get("A")).doesNotHaveDuplicates();
        assertThat(levelsOf(pool).keySet()).containsAll(first.byTaskType().get("A"));
    }
}
