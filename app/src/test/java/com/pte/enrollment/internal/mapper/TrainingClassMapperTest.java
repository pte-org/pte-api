package com.pte.enrollment.internal.mapper;

import com.pte.enrollment.domain.TrainingClass;
import com.pte.enrollment.domain.enums.ClassStatus;
import com.pte.enrollment.internal.dto.response.ClassResponse;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TrainingClassMapperTest {

    @Test
    void toResponse_mapsAllFieldsAndUsesPassedProgramPublicId() {
        TrainingClass trainingClass = new TrainingClass();
        trainingClass.setPublicId(UUID.randomUUID());
        trainingClass.setName("12A1");
        trainingClass.setStatus(ClassStatus.ACTIVE);
        UUID programPublicId = UUID.randomUUID();

        ClassResponse response = TrainingClassMapper.toResponse(trainingClass, programPublicId);

        assertThat(response.publicId()).isEqualTo(trainingClass.getPublicId());
        assertThat(response.programPublicId()).isEqualTo(programPublicId);
        assertThat(response.name()).isEqualTo("12A1");
        assertThat(response.status()).isEqualTo("ACTIVE");
    }
}
