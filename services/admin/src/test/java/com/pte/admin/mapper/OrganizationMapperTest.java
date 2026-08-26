package com.pte.admin.mapper;

import com.pte.admin.domain.Organization;
import com.pte.admin.domain.enums.FacilityType;
import com.pte.admin.domain.enums.OrganizationStatus;
import com.pte.admin.dto.response.OrganizationResponse;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrganizationMapperTest {

    @Test
    void toResponse_mapsAllFieldsAndUsesPassedTenantPublicId() {
        Organization organization = new Organization();
        organization.setPublicId(UUID.randomUUID());
        organization.setName("Downtown Branch");
        organization.setAddress("123 Main St");
        organization.setFacilityType(FacilityType.BRANCH);
        organization.setStatus(OrganizationStatus.ACTIVE);
        UUID tenantPublicId = UUID.randomUUID();

        OrganizationResponse response = OrganizationMapper.toResponse(organization, tenantPublicId);

        assertThat(response.publicId()).isEqualTo(organization.getPublicId());
        assertThat(response.tenantPublicId()).isEqualTo(tenantPublicId);
        assertThat(response.name()).isEqualTo("Downtown Branch");
        assertThat(response.address()).isEqualTo("123 Main St");
        assertThat(response.facilityType()).isEqualTo("BRANCH");
        assertThat(response.status()).isEqualTo("ACTIVE");
    }
}
