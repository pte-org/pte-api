package com.pte.tenancy.internal.mapper;

import com.pte.tenancy.domain.Organization;
import com.pte.tenancy.domain.enums.FacilityType;
import com.pte.tenancy.domain.enums.OrganizationStatus;
import com.pte.tenancy.internal.dto.response.OrganizationResponse;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrganizationMapperTest {

    @Test
    void toResponse_mapsAllFields() {
        Organization organization = new Organization();
        organization.setPublicId(UUID.randomUUID());
        organization.setName("Downtown Branch");
        organization.setAddress("123 Main St");
        organization.setFacilityType(FacilityType.BRANCH);
        organization.setStatus(OrganizationStatus.ACTIVE);

        OrganizationResponse response = OrganizationMapper.toResponse(organization);

        assertThat(response.publicId()).isEqualTo(organization.getPublicId());
        assertThat(response.name()).isEqualTo("Downtown Branch");
        assertThat(response.address()).isEqualTo("123 Main St");
        assertThat(response.facilityType()).isEqualTo("BRANCH");
        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    void toResponse_mapsIdentityAndPackageFields() {
        Organization organization = new Organization();
        organization.setPublicId(UUID.randomUUID());
        organization.setName("Acme School");
        organization.setOrganizationType("SCHOOL");
        organization.setTaxCode("0123456789");
        organization.setStatus(OrganizationStatus.ACTIVE);
        organization.setPackageName("starter");
        organization.setStudentLimit(500);

        OrganizationResponse response = OrganizationMapper.toResponse(organization);

        assertThat(response.name()).isEqualTo("Acme School");
        assertThat(response.taxCode()).isEqualTo("0123456789");
        assertThat(response.organizationType()).isEqualTo("SCHOOL");
        assertThat(response.packageName()).isEqualTo("starter");
        assertThat(response.studentLimit()).isEqualTo(500);
    }

    @Test
    void toResponse_includesBrandingFields() {
        Organization organization = new Organization();
        organization.setPublicId(UUID.randomUUID());
        organization.setName("Acme School");
        organization.setOrganizationType("SCHOOL");
        organization.setStatus(OrganizationStatus.ACTIVE);
        organization.setPackageName("starter");
        organization.setStudentLimit(500);
        organization.updateBranding("https://cdn.example.com/logo.png", "#1A2B3C");

        OrganizationResponse response = OrganizationMapper.toResponse(organization);

        assertThat(response.logoUrl()).isEqualTo("https://cdn.example.com/logo.png");
        assertThat(response.primaryColor()).isEqualTo("#1A2B3C");
    }
}
