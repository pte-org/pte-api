package com.pte.identity;

import java.util.List;
import java.util.UUID;

/**
 * Published after a roster import created Student accounts that came with a class name. Identity only stores the
 * name as text; the enrollment module listens and puts each student into the class of that name when there is
 * exactly one such class in the organization.
 */
public record StudentsImportedEvent(UUID tenantId, List<ImportedStudent> students) {

    public StudentsImportedEvent {
        students = students == null ? List.of() : List.copyOf(students);
    }

    /** A newly created Student and the class name written in the roster row. */
    public record ImportedStudent(UUID studentPublicId, String className) {
    }
}
