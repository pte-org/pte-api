package com.pte.notification.repository;

import com.pte.notification.domain.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    Optional<NotificationLog> findByPublicId(UUID publicId);

    List<NotificationLog> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
