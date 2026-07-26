package com.pte.examdelivery.dto.response;

import java.util.UUID;

/** {@code completed=true} once every task is done/expired; {@code task} is null in that case. */
public record AttemptTaskResponse(UUID attemptPublicId, String attemptStatus, boolean completed, TaskView task) {
}
