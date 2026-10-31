package com.pte.chat.internal.service.quota;

import java.time.LocalDate;
import java.util.UUID;

public record ChatQuotaTicket(UUID studentId, LocalDate usageDate) {
}
