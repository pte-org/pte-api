package com.pte.itembank.internal.repository;

import com.pte.itembank.domain.TaskRuntimeContract;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TaskRuntimeContractRepository extends JpaRepository<TaskRuntimeContract, Long> {

    Optional<TaskRuntimeContract> findByScreenKeyAndContractVersionAndDeletedFalse(
            String screenKey, int contractVersion);

    List<TaskRuntimeContract> findAllByScreenKeyInAndContractVersionInAndDeletedFalse(
            Collection<String> screenKeys, Collection<Integer> versions);

    List<TaskRuntimeContract> findAllByStatusAndDeletedFalseOrderByScreenKeyAscContractVersionAsc(String status);
}
