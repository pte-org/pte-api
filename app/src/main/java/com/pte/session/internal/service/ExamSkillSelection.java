package com.pte.session.internal.service;

import com.pte.assessment.dto.response.ExamDefinitionResponse;
import com.pte.session.internal.constant.SessionConstants;
import com.pte.session.internal.exception.ExamDraftConfigurationException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Resolves which skills and how many retries an exam draft may carry, against its definition. */
@Component
class ExamSkillSelection {

    Set<String> resolveTemplateSkills(ExamDefinitionResponse template) {
        if (template == null || template.items() == null || template.items().isEmpty()) {
            throw new ExamDraftConfigurationException(SessionConstants.TEMPLATE_SKILLS_UNAVAILABLE);
        }
        LinkedHashSet<String> skills = new LinkedHashSet<>();
        for (var item : template.items()) {
            String section = normalizeSkill(item.section());
            if (section == null) {
                continue;
            }
            if (!SessionConstants.SUPPORTED_EXAM_SKILLS.contains(section)) {
                throw new ExamDraftConfigurationException(SessionConstants.TEMPLATE_SKILLS_UNAVAILABLE);
            }
            skills.add(section);
        }
        if (skills.isEmpty()) {
            throw new ExamDraftConfigurationException(SessionConstants.TEMPLATE_SKILLS_UNAVAILABLE);
        }
        return skills;
    }

    Set<String> resolveSelectedSkills(List<String> requestedSkills, Set<String> templateSkills) {
        if (requestedSkills == null) {
            return new LinkedHashSet<>(templateSkills);
        }
        if (requestedSkills.isEmpty()) {
            throw new ExamDraftConfigurationException(SessionConstants.SKILLS_REQUIRED);
        }
        LinkedHashSet<String> selectedSkills = new LinkedHashSet<>();
        for (String value : requestedSkills) {
            String skill = normalizeSkill(value);
            if (skill == null || !templateSkills.contains(skill)) {
                throw new ExamDraftConfigurationException(SessionConstants.SKILLS_NOT_IN_TEMPLATE);
            }
            if (!selectedSkills.add(skill)) {
                throw new ExamDraftConfigurationException(SessionConstants.SKILLS_DUPLICATE);
            }
        }
        if (!selectedSkills.equals(templateSkills)) {
            throw new ExamDraftConfigurationException(SessionConstants.SKILLS_FULL_TEMPLATE_REQUIRED);
        }
        return selectedSkills;
    }

    String normalizeSkill(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(java.util.Locale.ROOT);
    }

    /** Every student sits an exam once, as in the real test; a retry count above 0 is refused. */
    int resolveRetryCount(Integer retries) {
        if (retries != null && retries != 0) {
            throw new ExamDraftConfigurationException(SessionConstants.RETRY_COUNT_INVALID);
        }
        return 0;
    }
}
