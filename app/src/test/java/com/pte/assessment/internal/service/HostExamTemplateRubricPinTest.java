package com.pte.assessment.internal.service;

import com.pte.assessment.ActiveRubricVersionPin;
import com.pte.assessment.domain.ExamDefinition;
import com.pte.assessment.domain.enums.ExamDefinitionStatus;
import com.pte.assessment.domain.enums.TemplatePolicy;
import com.pte.assessment.dto.request.CreateHostExamTemplateRequest;
import com.pte.assessment.dto.response.ExamDefinitionResponse;
import com.pte.assessment.internal.repository.ExamDefinitionRepository;
import com.pte.assessment.internal.service.definition.HostExamTemplateService;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.pte.assessment.internal.service.ExamDefinitionRubricPinTest.assertNoActiveRubric;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Host templates copy the rubric pin of the template they start from (the platform default, or the host template
 * being cloned); only a source without a pin (legacy row) falls back to the ACTIVE rubric version.
 */
@ExtendWith(MockitoExtension.class)
class HostExamTemplateRubricPinTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID ACTIVE_RUBRIC = UUID.randomUUID();
    private static final UUID SOURCE_RUBRIC = UUID.randomUUID();
    private static final ActiveRubricVersionPin ACTIVE = () -> Optional.of(ACTIVE_RUBRIC);
    private static final ActiveRubricVersionPin NONE = Optional::empty;

    @Mock
    private ExamDefinitionRepository repository;

    private final CurrentUser host = new CurrentUser(UUID.randomUUID(), TENANT, List.of("HOST_ADMIN"));

    @Test
    void createFromDefaultCopiesThePlatformDefaultsPin() {
        ExamDefinition base = stubActiveDefault(SOURCE_RUBRIC);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ExamDefinitionResponse response = service(ACTIVE).createFromDefault(new CreateHostExamTemplateRequest("A"), host);

        assertThat(response.rubricVersionPublicId()).isEqualTo(SOURCE_RUBRIC);
        assertThat(base.getRubricVersionPublicId()).isEqualTo(SOURCE_RUBRIC);
    }

    @Test
    void createFromAnUnpinnedDefaultPinsTheActiveRubricVersion() {
        stubActiveDefault(null);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ExamDefinitionResponse response = service(ACTIVE).createFromDefault(new CreateHostExamTemplateRequest("A"), host);

        assertThat(response.rubricVersionPublicId()).isEqualTo(ACTIVE_RUBRIC);
    }

    @Test
    void createFromAnUnpinnedDefaultWithoutAnActiveRubricVersionFails() {
        stubActiveDefault(null);
        HostExamTemplateService service = service(NONE);

        assertNoActiveRubric(() -> service.createFromDefault(new CreateHostExamTemplateRequest("A"), host));
        verify(repository, never()).save(any());
    }

    @Test
    void hostCloneKeepsTheSourcePinEvenWhenAnotherRubricVersionIsActive() {
        ExamDefinition source = stubOwned(ExamDefinitionStatus.ACTIVE, SOURCE_RUBRIC);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ExamDefinitionResponse clone = service(ACTIVE).cloneToDraft(source.getPublicId(), host);

        assertThat(clone.rubricVersionPublicId()).isEqualTo(SOURCE_RUBRIC);
        assertThat(source.getRubricVersionPublicId()).isEqualTo(SOURCE_RUBRIC);
    }

    @Test
    void hostCloneOfAnUnpinnedSourceWithoutAnActiveRubricVersionFails() {
        ExamDefinition source = stubOwned(ExamDefinitionStatus.RETIRED, null);
        HostExamTemplateService service = service(NONE);

        assertNoActiveRubric(() -> service.cloneToDraft(source.getPublicId(), host));
        verify(repository, never()).save(any());
    }

    @Test
    void retiringAHostTemplateNeverChangesItsPin() {
        ExamDefinition active = stubOwned(ExamDefinitionStatus.ACTIVE, SOURCE_RUBRIC);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ExamDefinitionResponse retired = service(ACTIVE).retire(active.getPublicId(), host);

        assertThat(retired.status()).isEqualTo("RETIRED");
        assertThat(retired.rubricVersionPublicId()).isEqualTo(SOURCE_RUBRIC);
    }

    private ExamDefinition stubActiveDefault(UUID pin) {
        ExamDefinition base = template(null, ExamDefinitionStatus.ACTIVE, pin);
        when(repository.findWithItemsByStatusAndTenantIdIsNull(ExamDefinitionStatus.ACTIVE))
                .thenReturn(Optional.of(base));
        return base;
    }

    private ExamDefinition stubOwned(ExamDefinitionStatus status, UUID pin) {
        ExamDefinition source = template(TENANT, status, pin);
        source.setBaseDefinitionPublicId(UUID.randomUUID());
        when(repository.findWithItemsByPublicIdAndTenantId(source.getPublicId(), TENANT))
                .thenReturn(Optional.of(source));
        return source;
    }

    private static ExamDefinition template(UUID tenantId, ExamDefinitionStatus status, UUID pin) {
        ExamDefinition template = new ExamDefinition();
        template.setPublicId(UUID.randomUUID());
        template.setCode(tenantId == null ? "PTE_FULL" : "HOST_A");
        template.setVersion(1);
        template.setName("Template");
        template.setStatus(status);
        template.setTemplatePolicy(tenantId == null ? TemplatePolicy.STANDARD_PTE : TemplatePolicy.CUSTOM);
        template.setTenantId(tenantId);
        template.setRubricVersionPublicId(pin);
        return template;
    }

    private HostExamTemplateService service(ActiveRubricVersionPin pin) {
        return new HostExamTemplateService(repository, null, null, null, null, com.pte.TestClocks.fixed(), null,
                null, pin);
    }
}
