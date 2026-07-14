package com.aptis.modules.iam.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aptis.modules.iam.domain.Host;

public interface HostRepository extends JpaRepository<Host, Long> {

    Optional<Host> findByContactEmail(String contactEmail);

    Optional<Host> findByCode(String code);
}
