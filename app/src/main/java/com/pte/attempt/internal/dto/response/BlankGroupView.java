package com.pte.attempt.internal.dto.response;

import java.util.List;

/**
 * One independently-choosable blank within a {@code FILL_IN_THE_BLANKS_DROPDOWN}
 * task — its {@code options} are distinct from every other blank's, unlike the
 * shared word bank {@code TaskView.options} carries for {@code FILL_IN_THE_BLANKS_DRAG_AND_DROP}.
 * Absent (null) on every other task type.
 */
public record BlankGroupView(Integer blankIndex, List<OptionView> options) {
}
