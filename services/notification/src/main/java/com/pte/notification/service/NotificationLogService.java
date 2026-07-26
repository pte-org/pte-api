package com.pte.notification.service;

import com.pte.common.security.CurrentUser;
import com.pte.notification.dto.response.NotificationLogResponse;
import com.pte.notification.mapper.NotificationLogMapper;
import com.pte.notification.repository.NotificationLogRepository;
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
