package com.aptis.modules.tenancy.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aptis.modules.tenancy.domain.Organization;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    Optional<Organization> findByRepresentativeEmail(String representativeEmail);

    Optional<Organization> findByContractCode(String contractCode);
}
