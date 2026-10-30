package com.pte.practice.internal.repository;

import com.pte.practice.internal.domain.PracticeMockTestItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PracticeMockTestItemRepository extends JpaRepository<PracticeMockTestItem, Long> {

    List<PracticeMockTestItem> findByMockTestIdOrderByOrderIndexAsc(Long mockTestId);

    List<PracticeMockTestItem> findByMockTestIdInOrderByMockTestIdAscOrderIndexAsc(Collection<Long> mockTestIds);
}
