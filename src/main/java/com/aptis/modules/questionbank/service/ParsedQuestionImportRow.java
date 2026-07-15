package com.aptis.modules.questionbank.service;

import java.util.List;

import com.aptis.modules.questionbank.domain.Skill;

/** One validated row from a bulk-import Excel file, ready to persist. Internal to the import flow. */
record ParsedQuestionImportRow(
        int rowNumber,
        Skill skill,
        String content,
        List<String> options,
        String correctAnswer,
        String audioFilename) {
}
