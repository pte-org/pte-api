package com.pte.admin.mapper;

import com.pte.admin.domain.ClassMembership;
import com.pte.admin.domain.Organization;
import com.pte.admin.domain.Program;
import com.pte.admin.domain.StudentClass;
import com.pte.admin.dto.response.ClassMembershipResponse;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ClassMembershipMapperTest {

    @Test
    void toResponse_mapsAllFieldsIncludingNamesFromLoadedAssociations() {
        Organization organization = new Organization();
        organization.setPublicId(UUID.randomUUID());

        Program program = new Program();
        program.setPublicId(UUID.randomUUID());
        program.setName("Khối 12");
        program.setOrganization(organization);

        StudentClass studentClass = new StudentClass();
        studentClass.setPublicId(UUID.randomUUID());
        studentClass.setName("12A1");
        studentClass.setProgram(program);

        ClassMembership membership = new ClassMembership();
        membership.setPublicId(UUID.randomUUID());
        membership.setStudentClass(studentClass);
        membership.setStudentPublicId(UUID.randomUUID());

        ClassMembershipResponse response = ClassMembershipMapper.toResponse(membership);

        assertThat(response.publicId()).isEqualTo(membership.getPublicId());
        assertThat(response.classPublicId()).isEqualTo(studentClass.getPublicId());
        assertThat(response.className()).isEqualTo("12A1");
        assertThat(response.programPublicId()).isEqualTo(program.getPublicId());
        assertThat(response.programName()).isEqualTo("Khối 12");
        assertThat(response.studentPublicId()).isEqualTo(membership.getStudentPublicId());
    }
}
