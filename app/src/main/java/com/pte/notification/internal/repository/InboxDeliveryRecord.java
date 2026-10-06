package com.pte.notification.internal.repository;

import com.pte.notification.domain.enums.InboxNotificationType;
import java.util.UUID;

public record InboxDeliveryRecord(UUID deliveryPublicId, UUID contentPublicId,
        UUID recipientPublicId, UUID tenantId, InboxNotificationType type) { }
