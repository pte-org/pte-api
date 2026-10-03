package com.pte.notification.internal.repository;

import java.util.UUID;

/** A fencing token, not a message payload. */
public record InboxDeliveryClaim(UUID publicId, UUID token, int attempts) { }
