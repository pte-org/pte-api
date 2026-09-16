package com.pte.identity.internal.repository;

import com.pte.identity.internal.domain.LoginHash;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LoginHashRepository extends JpaRepository<LoginHash, Long> {

    Optional<LoginHash> findByUserId(Long userId);
}
