package com.pte.enrollment.domain;

import com.pte.tenancy.domain.Organization;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TrainingClassTest {

    @Test
    void setProgramTakesTheOwningOrganizationFromTheProgram() {
        UUID organizationPublicId = UUID.randomUUID();
        Organization organization = new Organization();
        organization.setPublicId(organizationPublicId);
        Program program = new Program();
        program.setOrganization(organization);

        TrainingClass trainingClass = new TrainingClass();
        trainingClass.setProgram(program);

        assertThat(trainingClass.getProgram()).isSameAs(program);
        assertThat(trainingClass.getOrganizationId()).isEqualTo(organizationPublicId);
    }

    @Test
    void setProgramWithoutOrganizationLeavesOrganizationUnset() {
        TrainingClass trainingClass = new TrainingClass();
        trainingClass.setProgram(new Program());

        assertThat(trainingClass.getOrganizationId()).isNull();
    }
}
