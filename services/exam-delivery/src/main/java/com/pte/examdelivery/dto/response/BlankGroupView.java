package com.pte.examdelivery.dto.response;

import java.util.List;

/**
 * One independently-choosable blank within a {@code FILL_BLANKS_READING_WRITING}
 * task — its {@code options} are distinct from every other blank's, unlike the
 * shared word bank {@code TaskView.options} carries for {@code FILL_BLANKS_READING}.
 * Absent (null) on every other task type.
 */
public record BlankGroupView(Integer blankIndex, List<OptionView> options) {
}
