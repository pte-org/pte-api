package com.pte.session.dto.response;

import com.pte.shared.web.PageMeta;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AttendanceHistoryView(
        UUID sessionPublicId,
        UUID studentPublicId,
        List<AttendanceHistoryItemView> data,
        PageMeta meta,
        Instant asOf
) {
}
