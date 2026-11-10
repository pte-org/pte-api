package com.pte.assessment.internal.service;

import com.pte.assessment.ActiveRubricVersionPin;
import com.pte.assessment.domain.ExamDefinition;
import com.pte.assessment.domain.enums.ExamDefinitionStatus;
import com.pte.assessment.dto.request.CreateExamDefinitionRequest;
import com.pte.assessment.dto.response.ExamDefinitionResponse;
import com.pte.assessment.internal.constant.ExamDefinitionConstants;
import com.pte.assessment.internal.exception.NoActiveRubricVersionException;
import com.pte.assessment.internal.repository.ExamDefinitionRepository;
import com.pte.assessment.internal.service.definition.ExamDefinitionAdminService;
import com.pte.shared.exception.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Platform templates pin a rubric version when they are created: a new draft and a vendor clone (a new version of
 * the family) both pin the ACTIVE rubric version; the source keeps its own pin; nothing is created without one.
 */
@ExtendWith(MockitoExtension.class)
class ExamDefinitionRubricPinTest {

    private static final UUID ACTIVE_RUBRIC = UUID.randomUUID();
    private static final UUID OLDER_RUBRIC = UUID.randomUUID();
    private static final String CODE = "PTE_FULL";

    @Mock
    private ExamDefinitionRepository repository;

    @Test
    void createDraftPinsTheActiveRubricVersion() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ExamDefinitionResponse response = service(() -> Optional.of(ACTIVE_RUBRIC))
                .createDraft(new CreateExamDefinitionRequest(CODE, "Full template"));

        assertThat(response.rubricVersionPublicId()).isEqualTo(ACTIVE_RUBRIC);
    }

    @Test
    void createDraftWithoutAnActiveRubricVersionFailsAndStoresNothing() {
        ExamDefinitionAdminService service = service(Optional::empty);

        assertNoActiveRubric(() -> service.createDraft(new CreateExamDefinitionRequest(CODE, "Full template")));
        verify(repository, never()).save(any());
    }

    @Test
    void aServiceBuiltWithoutThePinPortCannotCreateUnpinnedDrafts() {
        ExamDefinitionAdminService unpinned = new ExamDefinitionAdminService(repository, null, null, null, null, null,
                null, com.pte.TestClocks.fixed());

        assertNoActiveRubric(() -> unpinned.createDraft(new CreateExamDefinitionRequest(CODE, "Full template")));
        verify(repository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = ExamDefinitionStatus.class, names = {"ACTIVE", "RETIRED"})
    void vendorCloneIsANewVersionAndPinsTheActiveRubricWhileTheSourceKeepsItsPin(ExamDefinitionStatus status) {
        ExamDefinition source = stubSource(status);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ExamDefinitionResponse clone = service(() -> Optional.of(ACTIVE_RUBRIC)).cloneToDraft(source.getPublicId());

        assertThat(clone.rubricVersionPublicId()).isEqualTo(ACTIVE_RUBRIC);
        assertThat(source.getRubricVersionPublicId()).isEqualTo(OLDER_RUBRIC);
        assertThat(source.getStatus()).isEqualTo(status);
    }

    @Test
    void vendorCloneWithoutAnActiveRubricVersionFailsAndStoresNothing() {
        ExamDefinition source = stubSource(ExamDefinitionStatus.ACTIVE);
        ExamDefinitionAdminService service = service(Optional::empty);

        assertNoActiveRubric(() -> service.cloneToDraft(source.getPublicId()));
        verify(repository, never()).save(any());
        assertThat(source.getRubricVersionPublicId()).isEqualTo(OLDER_RUBRIC);
    }

    private ExamDefinition stubSource(ExamDefinitionStatus status) {
        ExamDefinition source = new ExamDefinition();
        source.setPublicId(UUID.randomUUID());
        source.setCode(CODE);
        source.setVersion(2);
        source.setName("Full template");
        source.setStatus(status);
        source.setRubricVersionPublicId(OLDER_RUBRIC);
        when(repository.findWithItemsByPublicIdAndTenantIdIsNull(source.getPublicId()))
                .thenReturn(Optional.of(source));
        when(repository.findAllByCodeForUpdate(CODE)).thenReturn(List.of(source));
        when(repository.findMaxVersionByCode(CODE)).thenReturn(2);
        return source;
    }

    private ExamDefinitionAdminService service(ActiveRubricVersionPin pin) {
        return new ExamDefinitionAdminService(repository, null, null, null, null, null, null,
                com.pte.TestClocks.fixed(), null, pin);
    }

    static void assertNoActiveRubric(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(NoActiveRubricVersionException.class)
                .isInstanceOfSatisfying(DomainException.class, error -> {
                    assertThat(error.getCode()).isEqualTo(ExamDefinitionConstants.NO_ACTIVE_RUBRIC_VERSION);
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });
    }
}
