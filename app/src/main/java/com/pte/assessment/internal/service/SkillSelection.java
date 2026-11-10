package com.pte.assessment.internal.service;

import com.pte.assessment.dto.response.ExamDefinitionResponse;
import com.pte.assessment.internal.exception.InvalidSectionException;
import com.pte.assessment.internal.exception.InvalidSkillSelectionException;
import com.pte.itembank.domain.enums.PteSection;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Turns the skills a caller asked for into the sections an exam covers, rejecting anything invalid. */
final class SkillSelection {

    private static final int SECTION_COUNT = PteSection.values().length;

    private SkillSelection() {
    }

    /** Validates skill input before template lookup. */
    static void validate(Set<String> skills) {
        if (skills == null || skills.isEmpty() || skills.size() > SECTION_COUNT) {
            throw new InvalidSkillSelectionException();
        }
        skills.forEach(SkillSelection::parseSection);
    }

    /** The sections a caller's skills name; used for the unscoped random generation. */
    static Set<PteSection> parse(Set<String> skills) {
        if (skills == null || skills.isEmpty() || skills.size() > SECTION_COUNT) {
            throw new InvalidSkillSelectionException();
        }
        return skills.stream().map(SkillSelection::parseSection).collect(Collectors.toSet());
    }

    /** The sections to use for a template: the requested skills, or all the template has when none are given. */
    static Set<PteSection> resolve(ExamDefinitionResponse template, Set<String> requestedSkills) {
        Set<PteSection> available = template.items().stream()
                .map(item -> parseSection(item.section()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (available.isEmpty()) {
            throw new InvalidSkillSelectionException();
        }
        if (requestedSkills == null || requestedSkills.isEmpty()) {
            return available;
        }
        Set<PteSection> selected = requestedSkills.stream()
                .map(skill -> skill == null ? null : skill.trim().toUpperCase(Locale.ROOT))
                .map(SkillSelection::parseSection)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!available.containsAll(selected)) {
            throw new InvalidSkillSelectionException();
        }
        return selected;
    }

    private static PteSection parseSection(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidSectionException();
        }
        try {
            return PteSection.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new InvalidSectionException();
        }
    }
}
