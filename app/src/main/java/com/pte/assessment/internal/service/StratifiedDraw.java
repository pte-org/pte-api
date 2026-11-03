package com.pte.assessment.internal.service;

import com.pte.assessment.internal.service.assembly.StratifiedQuestionPicker;
import com.pte.assessment.internal.service.assembly.StratifiedQuestionPicker.Candidate;
import com.pte.assessment.internal.service.assembly.StratifiedQuestionPicker.Need;
import com.pte.assessment.internal.service.assembly.StratifiedQuestionPicker.Picked;
import com.pte.itembank.ExamCandidateService;
import com.pte.assessment.internal.exception.InsufficientQuestionBankException;
import com.pte.itembank.domain.enums.CefrLevel;
import com.pte.itembank.domain.enums.PteSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Loads the exam-ready questions of the task types an exam needs and draws them to a CEFR mix. */
final class StratifiedDraw {

    private final ExamCandidateService candidateService;

    StratifiedDraw(ExamCandidateService candidateService) {
        this.candidateService = candidateService;
    }

    boolean isAvailable() {
        return candidateService != null;
    }

    Picked draw(List<Need> needs, Map<CefrLevel, Integer> mix, long seed) {
        Map<String, List<Candidate>> pool = new HashMap<>();
        for (Need need : needs) {
            pool.computeIfAbsent(need.taskTypeKey(), key -> candidateService.candidates(key).stream()
                    .map(candidate -> new Candidate(candidate.publicId(), CefrLevel.parseOrNull(candidate.cefrLevel())))
                    .toList());
        }
        return StratifiedQuestionPicker.pick(needs, pool, mix, seed);
    }

    /** The drawn questions in the order of the needs; a task type the bank could not fill is a shortage. */
    List<ExamBlueprintAssembler.PickedItem> toItems(List<Need> needs, Picked picked) {
        List<ExamBlueprintAssembler.PickedItem> items = new ArrayList<>();
        List<InsufficientQuestionBankException.Shortage> shortages = new ArrayList<>();
        for (Need need : needs) {
            List<UUID> ids = picked.byTaskType().getOrDefault(need.taskTypeKey(), List.of());
            if (ids.size() < need.count()) {
                shortages.add(new InsufficientQuestionBankException.Shortage(need.taskTypeKey(), need.count(),
                        ids.size()));
            }
            PteSection section = PteSection.valueOf(need.skill());
            ids.forEach(id -> items.add(new ExamBlueprintAssembler.PickedItem(id, section)));
        }
        if (!shortages.isEmpty()) {
            throw new InsufficientQuestionBankException(shortages);
        }
        return items;
    }
}
