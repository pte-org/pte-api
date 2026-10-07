package com.pte.identity.internal.repository;

import com.pte.identity.internal.domain.PracticeIdentityMembership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PracticeIdentityMembershipRepository extends JpaRepository<PracticeIdentityMembership, Long> {

    List<PracticeIdentityMembership> findByIdentityIdAndDeletedFalse(Long identityId);
}
