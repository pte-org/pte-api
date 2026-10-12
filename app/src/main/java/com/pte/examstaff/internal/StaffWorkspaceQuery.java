package com.pte.examstaff.internal;

import com.pte.session.StaffSessionFilter;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;

public record StaffWorkspaceQuery(String from, String to, UUID sessionPublicId, String sessionStatus,
        String workStatus, String publicationStatus, Integer page, Integer size, String direction) {

    StaffSessionFilter validate() {
        if (pageNumber() < 0 || pageSize() < 1 || pageSize() > StaffWorkspaceConstants.MAX_SIZE
                || !StaffWorkspaceConstants.WORK_STATUSES.contains(work())
                || !StaffWorkspaceConstants.PUBLICATION_STATUSES.contains(publication())
                || direction != null && !direction.equals("asc") && !direction.equals("desc")) {
            throw StaffWorkspaceException.invalidQuery();
        }
        try {
            if (sessionStatus != null && !StaffWorkspaceConstants.SESSION_STATUSES.contains(sessionStatus)) {
                throw StaffWorkspaceException.invalidQuery();
            }
            Instant start = from == null ? null : Instant.parse(from);
            Instant end = to == null ? null : Instant.parse(to);
            if (start != null && end != null && !start.isBefore(end)) { throw StaffWorkspaceException.invalidQuery(); }
            return new StaffSessionFilter(start, end, sessionPublicId, sessionStatus, "asc".equals(direction));
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            throw StaffWorkspaceException.invalidQuery();
        }
    }

    int pageNumber() { return page == null ? 0 : page; }
    int pageSize() { return size == null ? StaffWorkspaceConstants.DEFAULT_SIZE : size; }
    String work() { return workStatus == null ? "ALL" : workStatus; }
    String publication() { return publicationStatus == null ? "ALL" : publicationStatus; }
}
