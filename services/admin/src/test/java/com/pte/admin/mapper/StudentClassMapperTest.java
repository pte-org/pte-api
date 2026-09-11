package com.pte.admin.mapper;

import com.pte.admin.domain.StudentClass;
import com.pte.admin.domain.enums.ClassStatus;
import com.pte.admin.dto.response.ClassResponse;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StudentClassMapperTest {

    @Test
    void toResponse_mapsAllFieldsAndUsesPassedProgramPublicId() {
        StudentClass studentClass = new StudentClass();
        studentClass.setPublicId(UUID.randomUUID());
        studentClass.setName("12A1");
        studentClass.setStatus(ClassStatus.ACTIVE);
        UUID programPublicId = UUID.randomUUID();

        ClassResponse response = StudentClassMapper.toResponse(studentClass, programPublicId);

        assertThat(response.publicId()).isEqualTo(studentClass.getPublicId());
        assertThat(response.programPublicId()).isEqualTo(programPublicId);
        assertThat(response.name()).isEqualTo("12A1");
        assertThat(response.status()).isEqualTo("ACTIVE");
    }
}
