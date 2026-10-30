package com.pte.enrollment.internal.mapper;

import com.pte.enrollment.domain.ClassStudent;
import com.pte.tenancy.domain.Organization;
import com.pte.enrollment.domain.Program;
import com.pte.enrollment.domain.TrainingClass;
import com.pte.enrollment.internal.dto.response.ClassMembershipResponse;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ClassStudentMapperTest {

    @Test
    void toResponse_mapsAllFieldsIncludingNamesFromLoadedAssociations() {
        Organization organization = new Organization();
        organization.setPublicId(UUID.randomUUID());

        Program program = new Program();
        program.setPublicId(UUID.randomUUID());
        program.setName("Khối 12");
        program.setOrganization(organization);

        TrainingClass trainingClass = new TrainingClass();
        trainingClass.setPublicId(UUID.randomUUID());
        trainingClass.setName("12A1");
        trainingClass.setProgram(program);

        ClassStudent membership = new ClassStudent();
        membership.setPublicId(UUID.randomUUID());
        membership.setTrainingClass(trainingClass);
        membership.setStudentPublicId(UUID.randomUUID());

        ClassMembershipResponse response = ClassStudentMapper.toResponse(membership);

        assertThat(response.publicId()).isEqualTo(membership.getPublicId());
        assertThat(response.classPublicId()).isEqualTo(trainingClass.getPublicId());
        assertThat(response.className()).isEqualTo("12A1");
        assertThat(response.programPublicId()).isEqualTo(program.getPublicId());
        assertThat(response.programName()).isEqualTo("Khối 12");
        assertThat(response.studentPublicId()).isEqualTo(membership.getStudentPublicId());
    }
}
