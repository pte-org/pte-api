package com.pte.notification.mapper;

import com.pte.notification.domain.NotificationLog;
import com.pte.notification.dto.response.NotificationLogResponse;
import org.springframework.stereotype.Component;

@Component
public class NotificationLogMapper {

    public NotificationLogResponse toResponse(NotificationLog log) {
        return new NotificationLogResponse(log.getPublicId(), log.getRecipientEmail(), log.getNotificationType(),
                log.getSubject(), log.getStatus(), log.getSentAt());
    }
}
