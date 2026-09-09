package com.pte.admin.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ProgramTest {

    @Test
    void isCurrentlyActive_bothDatesNull_alwaysActive() {
        Program program = new Program();

        assertThat(program.isCurrentlyActive(LocalDate.of(2026, 1, 1))).isTrue();
    }

    @Test
    void isCurrentlyActive_todayWithinRange_isActive() {
        Program program = new Program();
        program.setStartDate(LocalDate.of(2026, 8, 1));
        program.setEndDate(LocalDate.of(2027, 5, 31));

        assertThat(program.isCurrentlyActive(LocalDate.of(2026, 9, 1))).isTrue();
        assertThat(program.isCurrentlyActive(LocalDate.of(2026, 8, 1))).isTrue();
        assertThat(program.isCurrentlyActive(LocalDate.of(2027, 5, 31))).isTrue();
    }

    @Test
    void isCurrentlyActive_todayBeforeStart_isNotActive() {
        Program program = new Program();
        program.setStartDate(LocalDate.of(2026, 8, 1));

        assertThat(program.isCurrentlyActive(LocalDate.of(2026, 7, 31))).isFalse();
    }

    @Test
    void isCurrentlyActive_todayAfterEnd_isNotActive() {
        Program program = new Program();
        program.setEndDate(LocalDate.of(2027, 5, 31));

        assertThat(program.isCurrentlyActive(LocalDate.of(2027, 6, 1))).isFalse();
    }
}
