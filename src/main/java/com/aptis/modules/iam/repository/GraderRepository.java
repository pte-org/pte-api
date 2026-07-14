package com.aptis.modules.iam.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.aptis.modules.iam.domain.Grader;

public interface GraderRepository extends JpaRepository<Grader, Long> {
    Optional<Grader> findByUsername(String username);
    boolean existsByUsername(String username);
}
