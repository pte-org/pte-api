package com.pte.proctoring.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.MonitoringAttemptView;
import com.pte.identity.IdentityService;
import com.pte.identity.dto.response.StudentDisplayNameView;
import com.pte.session.SessionService;
import com.pte.session.dto.request.RecordAttendanceRequest;
import com.pte.session.dto.response.AttendanceCommandReceiptView;
import com.pte.session.dto.response.AttendanceHistoryItemView;
import com.pte.session.dto.response.AttendanceHistoryView;
import com.pte.session.dto.response.AttendancePageView;
import com.pte.session.dto.response.AttendanceRowView;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AttendanceCoordinatorService {

    private final SessionService sessionService;
    private final AttemptService attemptService;
    private final IdentityService identityService;

    public AttendanceCoordinatorService(
            SessionService sessionService,
            AttemptService attemptService,
            IdentityService identityService) {
        this.sessionService = sessionService;
        this.attemptService = attemptService;
        this.identityService = identityService;
    }

    public AttendancePageView getAttendanceRoster(
            UUID sessionPublicId,
            String status,
            String search,
            int page,
            int size,
            CurrentUser caller,
            boolean isHost) {

        AttendancePageView baseView = sessionService.getAttendanceRoster(sessionPublicId, status, search, page, size, caller, isHost);

        if (baseView.data() == null || baseView.data().isEmpty()) {
            return baseView;
        }

        List<UUID> studentIds = baseView.data().stream()
                .map(AttendanceRowView::studentPublicId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<UUID, MonitoringAttemptView> attemptMap;
        try {
            attemptMap = attemptService.getLatestAttemptsForStudents(sessionPublicId, caller.tenantId(), studentIds);
        } catch (Exception e) {
            attemptMap = Collections.emptyMap();
        }

        Map<UUID, StudentDisplayNameView> nameMap;
        try {
            nameMap = identityService.getStudentDisplayNames(caller.tenantId(), studentIds);
        } catch (Exception e) {
            nameMap = Collections.emptyMap();
        }

        Map<UUID, MonitoringAttemptView> finalAttemptMap = attemptMap != null ? attemptMap : Collections.emptyMap();
        Map<UUID, StudentDisplayNameView> finalNameMap = nameMap != null ? nameMap : Collections.emptyMap();

        List<AttendanceRowView> enrichedRows = baseView.data().stream().map(row -> {
            MonitoringAttemptView attempt = finalAttemptMap.get(row.studentPublicId());
            String activityStatus = attempt != null && attempt.status() != null ? attempt.status().name() : null;
            Instant activityAt = null;
            if (attempt != null) {
                if (attempt.lastSeenAt() != null) {
                    activityAt = attempt.lastSeenAt();
                } else if (attempt.submittedAt() != null) {
                    activityAt = attempt.submittedAt();
                } else {
                    activityAt = attempt.startedAt();
                }
            }

            String displayName = row.displayName();
            String email = row.email();
            if ((displayName == null || displayName.isBlank()) && finalNameMap.containsKey(row.studentPublicId())) {
                displayName = finalNameMap.get(row.studentPublicId()).displayName();
                email = finalNameMap.get(row.studentPublicId()).email();
            }

            return new AttendanceRowView(
                    row.studentPublicId(),
                    row.enrollmentPublicId(),
                    displayName,
                    email,
                    row.status(),
                    row.version(),
                    row.lastActorPublicId(),
                    row.lastActorRole(),
                    row.lastActionAt(),
                    row.lastReasonOrNote(),
                    row.canEdit(),
                    row.blockingReason(),
                    activityStatus,
                    activityAt
            );
        }).toList();

        return new AttendancePageView(
                baseView.sessionPublicId(),
                enrichedRows,
                baseView.meta(),
                baseView.summary(),
                baseView.canEditSession(),
                baseView.sessionBlockingReason(),
                baseView.asOf(),
                baseView.opensAt(),
                baseView.closesAt(),
                baseView.attendanceWindowOpensAt()
        );
    }

    public AttendanceCommandReceiptView recordAttendance(
            UUID sessionPublicId,
            UUID studentPublicId,
            RecordAttendanceRequest request,
            CurrentUser caller,
            boolean isHost) {
        return sessionService.recordAttendance(sessionPublicId, studentPublicId, request, caller, isHost);
    }

    public AttendanceCommandReceiptView getReceipt(
            UUID sessionPublicId,
            UUID requestPublicId,
            CurrentUser caller,
            boolean isHost) {
        return sessionService.getAttendanceReceipt(sessionPublicId, requestPublicId, caller, isHost);
    }

    public AttendanceHistoryView getHistory(
            UUID sessionPublicId,
            UUID studentPublicId,
            int page,
            int size,
            CurrentUser caller,
            boolean isHost) {

        AttendanceHistoryView historyView = sessionService.getAttendanceHistory(sessionPublicId, studentPublicId, page, size, caller, isHost);

        if (historyView.data() == null || historyView.data().isEmpty()) {
            return historyView;
        }

        List<UUID> actorIds = historyView.data().stream()
                .map(AttendanceHistoryItemView::actorPublicId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<UUID, StudentDisplayNameView> actorNames;
        try {
            actorNames = identityService.getStudentDisplayNames(caller.tenantId(), actorIds);
        } catch (Exception e) {
            actorNames = Collections.emptyMap();
        }

        Map<UUID, StudentDisplayNameView> finalActorNames = actorNames != null ? actorNames : Collections.emptyMap();

        List<AttendanceHistoryItemView> enrichedItems = historyView.data().stream().map(item -> {
            String actorName = finalActorNames.containsKey(item.actorPublicId())
                    ? finalActorNames.get(item.actorPublicId()).displayName()
                    : null;
            return new AttendanceHistoryItemView(
                    item.id(),
                    item.requestPublicId(),
                    item.studentPublicId(),
                    item.enrollmentPublicId(),
                    item.actorPublicId(),
                    actorName,
                    item.actorRole(),
                    item.appliedVersion(),
                    item.previousStatus(),
                    item.newStatus(),
                    item.reasonOrNote(),
                    item.serverActionAt()
            );
        }).toList();

        return new AttendanceHistoryView(
                historyView.sessionPublicId(),
                historyView.studentPublicId(),
                enrichedItems,
                historyView.meta(),
                historyView.asOf()
        );
    }
}
