package com.pte.notification.internal.dto.request;

import jakarta.validation.constraints.NotNull;

/** {@code PATCH /announcements/{id}/status} body; {@code PUBLISH} needs the draft version the operator reviewed. */
public record AnnouncementStatusRequest(@NotNull AnnouncementStatusAction action, Long expectedDraftVersion) {
}
