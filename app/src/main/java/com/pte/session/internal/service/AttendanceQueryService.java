package com.pte.session.internal.service;

import com.pte.session.domain.ExamSession;
import com.pte.session.domain.enums.AttendanceStatus;
import com.pte.session.domain.enums.SessionStatus;
import com.pte.session.dto.response.AttendancePageView;
import com.pte.session.dto.response.AttendanceRowView;
import com.pte.session.dto.response.AttendanceSummaryView;
import com.pte.session.internal.exception.SessionNotFoundException;
import com.pte.session.internal.repository.ExamSessionRepository;
import com.pte.session.internal.repository.ExamSessionProctorRepository;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.web.PageMeta;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AttendanceQueryService {

    private final JdbcTemplate jdbcTemplate;
    private final ExamSessionRepository examSessionRepository;
    private final ExamSessionProctorRepository proctorRepository;
    private final Clock clock;

    @Autowired
    public AttendanceQueryService(
            JdbcTemplate jdbcTemplate,
            ExamSessionRepository examSessionRepository,
            ExamSessionProctorRepository proctorRepository,
            Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.examSessionRepository = examSessionRepository;
        this.proctorRepository = proctorRepository;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    public AttendanceQueryService(
            JdbcTemplate jdbcTemplate,
            ExamSessionRepository examSessionRepository,
            ExamSessionProctorRepository proctorRepository) {
        this(jdbcTemplate, examSessionRepository, proctorRepository, Clock.systemUTC());
    }

    @Transactional(readOnly = true)
    public AttendancePageView getRosterPage(
            UUID sessionPublicId,
            UUID tenantId,
            String statusFilter,
            String search,
            int requestedPage,
            int requestedSize,
            CurrentUser caller,
            boolean isHost) {

        ExamSession session = examSessionRepository.findByPublicIdAndTenantId(sessionPublicId, tenantId)
                .orElseThrow(SessionNotFoundException::new);

        if (!isHost) {
            boolean assigned = proctorRepository.isProctorAssigned(sessionPublicId, caller.userId(), tenantId);
            if (!assigned) {
                throw new com.pte.session.internal.exception.ProctorNotAssignedException();
            }
        }

        Instant now = clock.instant();
        Instant opensAt = session.getOpensAt();
        Instant closesAt = session.getClosesAt();
        Instant windowOpensAt = opensAt != null ? opensAt.minus(30, ChronoUnit.MINUTES) : null;

        boolean canEditSession = false;
        String sessionBlockingReason = null;

        if (session.getStatus() == SessionStatus.CANCELLED) {
            canEditSession = false;
            sessionBlockingReason = "SESSION_CANCELLED";
        } else if (isHost) {
            if (session.getStatus() == SessionStatus.SCHEDULED
                    || session.getStatus() == SessionStatus.OPEN
                    || session.getStatus() == SessionStatus.CLOSED) {
                canEditSession = true;
            } else {
                canEditSession = false;
                sessionBlockingReason = "SESSION_NOT_MODIFIABLE";
            }
        } else {
            if (session.getStatus() == SessionStatus.CLOSED || (closesAt != null && !now.isBefore(closesAt))) {
                canEditSession = false;
                sessionBlockingReason = "SESSION_CLOSED";
            } else if (session.getStatus() == SessionStatus.SCHEDULED || session.getStatus() == SessionStatus.OPEN) {
                if (windowOpensAt != null && now.isBefore(windowOpensAt)) {
                    canEditSession = false;
                    sessionBlockingReason = "BEFORE_ATTENDANCE_WINDOW";
                } else if (closesAt != null && !now.isBefore(closesAt)) {
                    canEditSession = false;
                    sessionBlockingReason = "SESSION_CLOSED";
                } else {
                    canEditSession = true;
                }
            } else {
                canEditSession = false;
                sessionBlockingReason = "SESSION_NOT_SCHEDULED_OR_OPEN";
            }
        }

        AttendanceSummaryView summary = getSummary(session.getId(), tenantId);

        int page = Math.max(requestedPage, 0);
        int size = requestedSize <= 0 ? 20 : Math.min(requestedSize, 100);

        List<Object> countParams = new ArrayList<>();
        countParams.add(session.getId());
        countParams.add(tenantId);

        StringBuilder whereClause = new StringBuilder(" WHERE e.session_id = ? AND e.tenant_id = ? AND e.deleted = FALSE AND e.status = 'ENROLLED' ");

        String normalizedStatus = statusFilter != null ? statusFilter.trim().toUpperCase() : null;
        if ("PRESENT".equals(normalizedStatus)) {
            whereClause.append(" AND e.attendance_status = 'PRESENT' ");
        } else if ("LATE".equals(normalizedStatus)) {
            whereClause.append(" AND e.attendance_status = 'LATE' ");
        } else if ("ABSENT".equals(normalizedStatus)) {
            whereClause.append(" AND e.attendance_status = 'ABSENT' ");
        } else if ("UNMARKED".equals(normalizedStatus)) {
            whereClause.append(" AND e.attendance_status = 'UNMARKED' ");
        }

        if (search != null && !search.isBlank()) {
            String trimmedSearch = search.trim();
            if (trimmedSearch.length() > 100) {
                trimmedSearch = trimmedSearch.substring(0, 100);
            }
            String escapedPattern = "%" + trimmedSearch.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_").toLowerCase() + "%";
            whereClause.append(" AND (LOWER(u.display_name) LIKE ? ESCAPE '\\' OR LOWER(u.email) LIKE ? ESCAPE '\\' OR CAST(e.student_public_id AS VARCHAR) = ?) ");
            countParams.add(escapedPattern);
            countParams.add(escapedPattern);
            countParams.add(trimmedSearch);
        }

        String countSql = "SELECT COUNT(e.id) FROM exam_registrations e "
                + "LEFT JOIN identity_staff_student_search_view u ON u.tenant_id = e.tenant_id AND u.student_public_id = e.student_public_id "
                + whereClause;

        Long totalFiltered = jdbcTemplate.queryForObject(countSql, Long.class, countParams.toArray());
        long totalElements = totalFiltered != null ? totalFiltered : 0L;
        int totalPages = size == 0 ? 1 : (int) Math.ceil((double) totalElements / (double) size);

        List<Object> dataParams = new ArrayList<>(countParams);
        dataParams.add(size);
        dataParams.add(page * size);

        String dataSql = "SELECT "
                + "  e.student_public_id, "
                + "  e.public_id AS enrollment_public_id, "
                + "  u.display_name, "
                + "  u.email, "
                + "  e.attendance_status, "
                + "  e.attendance_version, "
                + "  e.last_actor_public_id, "
                + "  e.last_actor_role, "
                + "  e.last_action_at, "
                + "  e.last_reason_or_note, "
                + "  e.identity_status, "
                + "  e.identity_note "
                + "FROM exam_registrations e "
                + "LEFT JOIN identity_staff_student_search_view u ON u.tenant_id = e.tenant_id AND u.student_public_id = e.student_public_id "
                + whereClause
                + "ORDER BY u.display_name ASC NULLS LAST, e.student_public_id ASC "
                + "LIMIT ? OFFSET ?";

        boolean finalCanEditSession = canEditSession;
        String finalSessionBlockingReason = sessionBlockingReason;

        List<AttendanceRowView> rows = jdbcTemplate.query(dataSql, (rs, rowNum) -> {
            UUID studentId = (UUID) rs.getObject("student_public_id");
            UUID enrollmentId = (UUID) rs.getObject("enrollment_public_id");
            String displayName = rs.getString("display_name");
            String email = rs.getString("email");
            Timestamp actionTs = rs.getTimestamp("last_action_at");
            return new AttendanceRowView(
                    studentId,
                    enrollmentId,
                    displayName,
                    email,
                    AttendanceStatus.valueOf(rs.getString("attendance_status")),
                    rs.getLong("attendance_version"),
                    (UUID) rs.getObject("last_actor_public_id"),
                    rs.getString("last_actor_role"),
                    actionTs != null ? actionTs.toInstant() : null,
                    rs.getString("last_reason_or_note"),
                    finalCanEditSession,
                    finalSessionBlockingReason,
                    null,
                    null,
                    rs.getString("identity_status"),
                    rs.getString("identity_note")
            );
        }, dataParams.toArray());

        boolean isFirst = page == 0;
        boolean isLast = page >= totalPages - 1;
        boolean hasNext = page < totalPages - 1;
        boolean hasPrevious = page > 0;

        PageMeta meta = new PageMeta(page, size, totalElements, totalPages, isFirst, isLast, hasNext, hasPrevious);

        return new AttendancePageView(
                sessionPublicId,
                rows,
                meta,
                summary,
                canEditSession,
                sessionBlockingReason,
                now,
                opensAt,
                closesAt,
                windowOpensAt
        );
    }

    public AttendanceSummaryView getSummary(Long sessionId, UUID tenantId) {
        String sql = "SELECT "
                + "  COUNT(e.id) AS total_enrollments, "
                + "  COUNT(CASE WHEN e.attendance_status = 'PRESENT' THEN 1 END) AS present_count, "
                + "  COUNT(CASE WHEN e.attendance_status = 'ABSENT' THEN 1 END) AS absent_count, "
                + "  COUNT(CASE WHEN e.attendance_status = 'LATE' THEN 1 END) AS late_count, "
                + "  COUNT(CASE WHEN e.attendance_status = 'UNMARKED' THEN 1 END) AS unmarked_count "
                + "FROM exam_registrations e "
                + "WHERE e.session_id = ? AND e.tenant_id = ? AND e.deleted = FALSE AND e.status = 'ENROLLED'";

        return jdbcTemplate.query(sql, rs -> {
            if (rs.next()) {
                return new AttendanceSummaryView(
                        rs.getLong("total_enrollments"),
                        rs.getLong("unmarked_count"),
                        rs.getLong("present_count"),
                        rs.getLong("absent_count"),
                        rs.getLong("late_count")
                );
            }
            return new AttendanceSummaryView(0L, 0L, 0L, 0L, 0L);
        }, sessionId, tenantId);
    }
}
