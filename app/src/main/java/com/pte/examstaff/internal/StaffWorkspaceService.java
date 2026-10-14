package com.pte.examstaff.internal;

import com.pte.identity.ExamStaffIdentityService;
import com.pte.identity.dto.response.ExamStaffAccountView;
import com.pte.scoring.StaffScoringService;
import com.pte.session.StaffScheduleService;
import com.pte.session.StaffSessionFilter;
import com.pte.session.dto.response.StaffSessionView;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.web.PagedResult;
import com.pte.shared.web.PageMeta;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

/** Host-only composition. Owner modules do the queries; one read snapshot covers pages and totals. */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 30)
public class StaffWorkspaceService {
    private final ExamStaffIdentityService identity;
    private final StaffScheduleService schedules;
    private final StaffScoringService scoring;

    public StaffWorkspaceService(ExamStaffIdentityService identity, StaffScheduleService schedules,
            StaffScoringService scoring) {
        this.identity = identity;
        this.schedules = schedules;
        this.scoring = scoring;
    }

    public ExamStaffAccountView detail(UUID id, CurrentUser caller) { return identity.get(id, caller); }

    public PagedResult<StaffSessionView> proctor(UUID id, StaffWorkspaceQuery query, CurrentUser caller) {
        requireRole(identity.get(id, caller), "PROCTOR");
        var filter = query.validate();
        var page = schedules.page(caller.tenantId(), id, filter, query.pageNumber(), query.pageSize());
        return new PagedResult<>(page.getContent(), meta(query.pageNumber(), query.pageSize(), page.getTotalElements()));
    }

    public ExaminerSessionPage examiner(UUID id, StaffWorkspaceQuery query, CurrentUser caller) {
        requireRole(identity.get(id, caller), "EXAMINER");
        var totals = new ExaminerOverviewAccumulator();
        var records = new ArrayList<ExaminerSessionView>();
        long offset = (long) query.pageNumber() * query.pageSize();
        long[] matched = { 0 };
        scanExaminer(id, query, caller, record -> {
            totals.accept(record.progress());
            if (query.work().equals("ALL") || query.work().equals(record.progress().workStatus())) {
                long index = matched[0]++;
                if (index >= offset && records.size() < query.pageSize()) { records.add(record); }
            }
        });
        return new ExaminerSessionPage(List.copyOf(records), meta(query.pageNumber(), query.pageSize(), matched[0]),
                Map.copyOf(totals.buckets));
    }

    public StaffOverviewView overview(UUID id, StaffWorkspaceQuery query, CurrentUser caller) {
        var account = identity.get(id, caller);
        var filter = query.validate();
        Map<String, Long> proctor = null;
        ExaminerOverviewView examiner = null;
        if (account.roles().contains("PROCTOR")) { proctor = proctorCounts(id, filter, caller); }
        if (account.roles().contains("EXAMINER")) {
            var totals = new ExaminerOverviewAccumulator();
            scanExaminer(id, query, caller, record -> totals.accept(record.progress()));
            examiner = totals.view();
        }
        return new StaffOverviewView(proctor, examiner);
    }

    private void scanExaminer(UUID id, StaffWorkspaceQuery query, CurrentUser caller,
            Consumer<ExaminerSessionView> consume) {
        var filter = query.validate();
        int page = 0;
        boolean more;
        do {
            var sessions = schedules.page(caller.tenantId(), null, filter, page++, StaffWorkspaceConstants.SCAN_SIZE);
            var ids = sessions.getContent().stream().map(StaffSessionView::sessionPublicId).toList();
            var progress = scoring.summarize(caller.tenantId(), id, ids);
            for (var session : sessions) {
                var work = progress.get(session.sessionPublicId());
                if (work != null && (query.publication().equals("ALL")
                        || query.publication().equals(work.publicationStatus()))) {
                    consume.accept(new ExaminerSessionView(session, work));
                }
            }
            more = sessions.hasNext();
        } while (more);
    }

    private Map<String, Long> proctorCounts(UUID id, StaffSessionFilter filter, CurrentUser caller) {
        var counts = new LinkedHashMap<String, Long>();
        for (var bucket : List.of("upcoming", "ongoing", "ended", "cancelled", "awaitingOpen", "preparing", "unscheduled")) {
            counts.put(bucket, 0L);
        }
        long total = 0;
        int page = 0;
        boolean more;
        Instant now = Instant.now();
        do {
            var sessions = schedules.page(caller.tenantId(), id, filter, page++, StaffWorkspaceConstants.SCAN_SIZE);
            for (var session : sessions) { counts.merge(proctorBucket(session, now), 1L, Long::sum); total++; }
            more = sessions.hasNext();
        } while (more);
        counts.put("totalAssignedSessions", total);
        return Map.copyOf(counts);
    }

    private String proctorBucket(StaffSessionView session, Instant now) {
        if (session.sessionStatus().equals("CLOSED")) { return "ended"; }
        if (session.sessionStatus().equals("CANCELLED")) { return "cancelled"; }
        if (session.opensAt() == null || session.closesAt() == null || !session.opensAt().isBefore(session.closesAt())) {
            return "unscheduled";
        }
        return switch (session.sessionStatus()) {
            case "OPEN" -> "ongoing";
            case "SCHEDULED" -> session.opensAt().isAfter(now) ? "upcoming" : "awaitingOpen";
            default -> "preparing";
        };
    }

    private void requireRole(ExamStaffAccountView account, String role) {
        if (!account.roles().contains(role)) {
            throw new StaffWorkspaceException(HttpStatus.FORBIDDEN, StaffWorkspaceConstants.FORBIDDEN_WORK);
        }
    }

    private PageMeta meta(int page, int size, long total) {
        int pages = (int) Math.min(Integer.MAX_VALUE, (total + size - 1) / size);
        boolean next = ((long) page + 1) * size < total;
        return new PageMeta(page, size, total, pages, page == 0, !next, next, page > 0);
    }
}
