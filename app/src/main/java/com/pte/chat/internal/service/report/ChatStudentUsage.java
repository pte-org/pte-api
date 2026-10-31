package com.pte.chat.internal.service.report;

import java.util.UUID;

/** How many messages one student sent over a period: the id and the count, never what was said. */
public record ChatStudentUsage(UUID studentId, int messages) {
}
