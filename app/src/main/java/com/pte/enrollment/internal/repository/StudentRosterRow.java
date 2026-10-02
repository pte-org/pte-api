package com.pte.enrollment.internal.repository;

import java.time.Instant;
/** Read-only tuple returned by the single-query student roster search. */
public interface StudentRosterRow {

    String getStudentPublicId();

    String getEmail();

    String getFullName();

    String getStudentCode();

    String getPhone();

    String getStatus();

    Instant getCreatedAt();

    String getProgramPublicId();

    String getProgramName();

    String getClassPublicId();

    String getClassName();

    String getUsername();

    boolean getMustChangePassword();
}
