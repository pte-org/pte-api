package com.pte.session;

import com.pte.session.dto.response.StaffSessionView;
import com.pte.session.internal.service.StaffScheduleQueryService;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import java.util.UUID;

/** Trusted-caller read facade; examstaff authorizes the Host and selected staff first. */
@Service
public class StaffScheduleService {
    private final StaffScheduleQueryService queries;
    public StaffScheduleService(StaffScheduleQueryService queries) { this.queries = queries; }
    public Page<StaffSessionView> page(UUID tenantId, UUID proctorId, StaffSessionFilter filter, int page, int size) {
        return queries.page(tenantId, proctorId, filter, page, size);
    }
}
