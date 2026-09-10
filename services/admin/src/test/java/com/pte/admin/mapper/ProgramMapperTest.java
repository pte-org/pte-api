package com.pte.admin.mapper;

import com.pte.admin.domain.Program;
import com.pte.admin.domain.enums.ProgramStatus;
import com.pte.admin.dto.response.ProgramResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProgramMapperTest {

    @Test
    void toResponse_mapsAllFieldsAndUsesPassedOrganizationPublicId() {
        Program program = new Program();
        program.setPublicId(UUID.randomUUID());
        program.setName("Khối 12");
        program.setDescription("Grade 12 cohort");
        program.setStartDate(LocalDate.of(2026, 8, 1));
        program.setEndDate(LocalDate.of(2027, 5, 31));
        program.setStatus(ProgramStatus.ACTIVE);
        UUID organizationPublicId = UUID.randomUUID();

        ProgramResponse response = ProgramMapper.toResponse(program, organizationPublicId);

        assertThat(response.publicId()).isEqualTo(program.getPublicId());
        assertThat(response.organizationPublicId()).isEqualTo(organizationPublicId);
        assertThat(response.name()).isEqualTo("Khối 12");
        assertThat(response.description()).isEqualTo("Grade 12 cohort");
        assertThat(response.startDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(response.endDate()).isEqualTo(LocalDate.of(2027, 5, 31));
        assertThat(response.status()).isEqualTo("ACTIVE");
    }
}
