package com.pte.notification.internal.service;

import com.pte.notification.internal.dto.response.NotificationLogResponse;
import com.pte.notification.internal.mapper.NotificationLogMapper;
import com.pte.notification.internal.repository.NotificationLogRepository;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Delivery-status audit review for hosts — read-only, tenant-scoped. */
@Service
public class NotificationLogService {

    private final NotificationLogRepository notificationLogRepository;
    private final NotificationLogMapper mapper;

    public NotificationLogService(NotificationLogRepository notificationLogRepository, NotificationLogMapper mapper) {
        this.notificationLogRepository = notificationLogRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<NotificationLogResponse> list(CurrentUser caller) {
        return notificationLogRepository.findByTenantIdOrderByCreatedAtDesc(caller.tenantId())
                .stream().map(mapper::toResponse).toList();
    }
}
