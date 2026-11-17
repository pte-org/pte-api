package com.pte.enrollment.internal.listener;

import com.pte.enrollment.domain.ClassStudent;
import com.pte.enrollment.domain.TrainingClass;
import com.pte.enrollment.domain.enums.ClassStatus;
import com.pte.enrollment.internal.repository.ClassStudentRepository;
import com.pte.enrollment.internal.repository.TrainingClassRepository;
import com.pte.identity.StudentsImportedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Puts students created by a roster import into the class named in their row. A name that matches no class, or more
 * than one active class, leaves the student unassigned for the Host to place by hand; a problem here never fails
 * the import itself.
 */
@Component
public class ImportedStudentClassAssigner {

    private static final Logger log = LoggerFactory.getLogger(ImportedStudentClassAssigner.class);

    private final TrainingClassRepository classRepository;
    private final ClassStudentRepository membershipRepository;

    public ImportedStudentClassAssigner(TrainingClassRepository classRepository,
            ClassStudentRepository membershipRepository) {
        this.classRepository = classRepository;
        this.membershipRepository = membershipRepository;
    }

    @TransactionalEventListener(fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onStudentsImported(StudentsImportedEvent event) {
        try {
            assign(event);
        } catch (RuntimeException exception) {
            log.warn("Could not place imported students into their classes for organization {}", event.tenantId(),
                    exception);
        }
    }

    private void assign(StudentsImportedEvent event) {
        List<UUID> ids = event.students().stream().map(StudentsImportedEvent.ImportedStudent::studentPublicId).toList();
        Set<UUID> alreadyPlaced = new HashSet<>();
        membershipRepository.findByOrganizationIdAndStudentPublicIdIn(event.tenantId(), ids)
                .forEach(membership -> alreadyPlaced.add(membership.getStudentPublicId()));
        List<ClassStudent> toCreate = new ArrayList<>();
        for (StudentsImportedEvent.ImportedStudent student : event.students()) {
            String name = student.className() == null ? "" : student.className().trim();
            if (name.isEmpty() || alreadyPlaced.contains(student.studentPublicId())) {
                continue;
            }
            List<TrainingClass> matches = classRepository
                    .findByOrganizationIdAndNameIgnoreCaseAndDeletedFalse(event.tenantId(), name).stream()
                    .filter(trainingClass -> trainingClass.getStatus() == ClassStatus.ACTIVE)
                    .filter(trainingClass -> trainingClass.getProgram() == null || !trainingClass.getProgram().isDeleted())
                    .toList();
            if (matches.size() != 1) {
                log.info("Imported student left unassigned: {} class(es) named \"{}\" in organization {}",
                        matches.size(), name, event.tenantId());
                continue;
            }
            ClassStudent membership = new ClassStudent();
            membership.setTrainingClass(matches.get(0));
            membership.setStudentPublicId(student.studentPublicId());
            membership.setOrganizationId(event.tenantId());
            toCreate.add(membership);
        }
        if (!toCreate.isEmpty()) {
            membershipRepository.saveAll(toCreate);
        }
    }
}
