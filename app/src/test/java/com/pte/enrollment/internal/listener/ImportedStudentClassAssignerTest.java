package com.pte.enrollment.internal.listener;

import com.pte.enrollment.domain.ClassStudent;
import com.pte.enrollment.domain.Program;
import com.pte.enrollment.domain.TrainingClass;
import com.pte.enrollment.domain.enums.ClassStatus;
import com.pte.enrollment.internal.repository.ClassStudentRepository;
import com.pte.enrollment.internal.repository.TrainingClassRepository;
import com.pte.identity.StudentsImportedEvent;
import com.pte.identity.StudentsImportedEvent.ImportedStudent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportedStudentClassAssignerTest {

    @Mock
    private TrainingClassRepository classRepository;
    @Mock
    private ClassStudentRepository membershipRepository;

    private ImportedStudentClassAssigner assigner;
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        assigner = new ImportedStudentClassAssigner(classRepository, membershipRepository);
    }

    private TrainingClass classNamed(String name, ClassStatus status) {
        TrainingClass trainingClass = new TrainingClass();
        trainingClass.setProgram(new Program());
        trainingClass.setName(name);
        trainingClass.setStatus(status);
        return trainingClass;
    }

    private StudentsImportedEvent event(ImportedStudent... students) {
        return new StudentsImportedEvent(tenantId, List.of(students));
    }

    @Test
    void aStudentIsPlacedInTheOneActiveClassWithThatNameIgnoringCaseAndSpaces() {
        UUID student = UUID.randomUUID();
        TrainingClass target = classNamed("PTE-Core-01", ClassStatus.ACTIVE);
        when(classRepository.findByOrganizationIdAndNameIgnoreCaseAndDeletedFalse(tenantId, "pte-core-01"))
                .thenReturn(List.of(target));

        assigner.onStudentsImported(event(new ImportedStudent(student, "  pte-core-01 ")));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ClassStudent>> saved = ArgumentCaptor.forClass(List.class);
        verify(membershipRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).singleElement().satisfies(membership -> {
            assertThat(membership.getStudentPublicId()).isEqualTo(student);
            assertThat(membership.getTrainingClass()).isSameAs(target);
            assertThat(membership.getOrganizationId()).isEqualTo(tenantId);
        });
    }

    @Test
    void anUnknownAmbiguousOrInactiveClassNameLeavesTheStudentUnassigned() {
        when(classRepository.findByOrganizationIdAndNameIgnoreCaseAndDeletedFalse(eq(tenantId), eq("Nowhere")))
                .thenReturn(List.of());
        when(classRepository.findByOrganizationIdAndNameIgnoreCaseAndDeletedFalse(eq(tenantId), eq("Twin")))
                .thenReturn(List.of(classNamed("Twin", ClassStatus.ACTIVE), classNamed("Twin", ClassStatus.ACTIVE)));
        when(classRepository.findByOrganizationIdAndNameIgnoreCaseAndDeletedFalse(eq(tenantId), eq("Closed")))
                .thenReturn(List.of(classNamed("Closed", ClassStatus.INACTIVE)));

        assigner.onStudentsImported(event(new ImportedStudent(UUID.randomUUID(), "Nowhere"),
                new ImportedStudent(UUID.randomUUID(), "Twin"), new ImportedStudent(UUID.randomUUID(), "Closed")));

        verify(membershipRepository, never()).saveAll(anyList());
    }

    @Test
    void aStudentAlreadyInAClassOrWithABlankNameIsLeftAlone() {
        UUID placed = UUID.randomUUID();
        ClassStudent existing = new ClassStudent();
        existing.setStudentPublicId(placed);
        when(membershipRepository.findByOrganizationIdAndStudentPublicIdIn(eq(tenantId), anyList()))
                .thenReturn(List.of(existing));

        assigner.onStudentsImported(event(new ImportedStudent(placed, "PTE-Core-01"),
                new ImportedStudent(UUID.randomUUID(), "   ")));

        verify(classRepository, never()).findByOrganizationIdAndNameIgnoreCaseAndDeletedFalse(any(), any());
        verify(membershipRepository, never()).saveAll(anyList());
    }

    @Test
    void aFailureWhilePlacingStudentsNeverEscapesToTheImport() {
        when(classRepository.findByOrganizationIdAndNameIgnoreCaseAndDeletedFalse(any(), any()))
                .thenThrow(new IllegalStateException("database down"));

        assigner.onStudentsImported(event(new ImportedStudent(UUID.randomUUID(), "PTE-Core-01")));

        verify(membershipRepository, never()).saveAll(anyList());
    }
}
