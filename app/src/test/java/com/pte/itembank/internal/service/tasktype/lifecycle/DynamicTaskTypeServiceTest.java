package com.pte.itembank.internal.service.tasktype.lifecycle;

import com.pte.itembank.TaskAuthoringRequirements;
import com.pte.itembank.TaskRuntimeContractDescriptor;
import com.pte.itembank.domain.QuestionType;
import com.pte.itembank.domain.enums.PteSection;
import com.pte.itembank.dto.request.CreateTaskTypeRequest;
import com.pte.itembank.dto.request.UpdateTaskTypeRequest;
import com.pte.itembank.dto.response.QuestionTypeResponse;
import com.pte.itembank.internal.exception.tasktype.TaskTypeStandardRuntimeImmutableException;
import com.pte.itembank.internal.repository.QuestionTypeRepository;
import com.pte.itembank.internal.service.tasktype.RuntimeContractResolver;
import com.pte.shared.security.CurrentUser;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DynamicTaskTypeServiceTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();
    private static final Instant LOCKED_AT = Instant.parse("2026-10-30T00:00:00Z");

    private final QuestionTypeRepository repository = mock(QuestionTypeRepository.class);
    private final RuntimeContractResolver contractResolver = mock(RuntimeContractResolver.class);
    private final CurrentUser admin = new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"));
    private final DynamicTaskTypeService service =
            new DynamicTaskTypeService(repository, contractResolver, null, null);

    @Test
    void aCustomTaskTypeInheritsTheAuthoringRequirementsOfItsRuntimeContract() {
        TaskAuthoringRequirements requirements = new TaskAuthoringRequirements(
                true, false, true, true, true, false, true, true, false);
        stubCreate(contract(requirements));

        service.create(createRequest("Listen and answer."), admin);

        QuestionType definition = savedDefinition();
        assertThat(definition.isRequiresAudioPrompt()).isTrue();
        assertThat(definition.isRequiresImagePrompt()).isFalse();
        assertThat(definition.isRequiresPromptText()).isTrue();
        assertThat(definition.isRequiresOptions()).isTrue();
        assertThat(definition.isRequiresCorrectAnswer()).isTrue();
        assertThat(definition.isRequiresWordCount()).isFalse();
        assertThat(definition.isRequiresSingleCorrectOption()).isTrue();
        assertThat(definition.isUsesOptionOrderAsCorrectPosition()).isTrue();
        assertThat(definition.isRequiresReferenceAnswer()).isFalse();
    }

    @Test
    void createStoresTheTrimmedInstructionAndReturnsIt() {
        stubCreate(contract(defaultRequirements()));

        QuestionTypeResponse response = service.create(createRequest("  Listen and answer.  "), admin);

        assertThat(savedDefinition().getInstruction()).isEqualTo("Listen and answer.");
        assertThat(response.instruction()).isEqualTo("Listen and answer.");
    }

    @Test
    void createRejectsAMissingBlankOrOverlongInstruction() {
        assertThat(VALIDATOR.validate(createRequest(null))).isNotEmpty();
        assertThat(VALIDATOR.validate(createRequest("   "))).isNotEmpty();
        assertThat(VALIDATOR.validate(createRequest("x".repeat(501)))).isNotEmpty();
        assertThat(VALIDATOR.validate(createRequest("x".repeat(500)))).isEmpty();
    }

    @Test
    void updateAcceptsAnOmittedInstructionButRejectsBlankOrOverlongText() {
        assertThat(VALIDATOR.validate(updateRequest(null))).isEmpty();
        assertThat(VALIDATOR.validate(updateRequest("   "))).isNotEmpty();
        assertThat(VALIDATOR.validate(updateRequest("x".repeat(501)))).isNotEmpty();
        assertThat(VALIDATOR.validate(updateRequest("Line one.\nLine two."))).isEmpty();
    }

    @Test
    void updateWithoutInstructionKeepsTheStoredText() {
        QuestionType definition = existingDefinition(null);
        stubUpdate(definition);

        QuestionTypeResponse response = service.update(definition.getPublicId(), updateRequest(null), admin);

        assertThat(definition.getInstruction()).isEqualTo("Original instruction.");
        assertThat(response.instruction()).isEqualTo("Original instruction.");
    }

    @Test
    void updateChangesTheInstructionOfARuntimeLockedTaskType() {
        QuestionType definition = existingDefinition(LOCKED_AT);
        stubUpdate(definition);

        QuestionTypeResponse response = service.update(definition.getPublicId(),
                updateRequest("  Revised instruction.  "), admin);

        assertThat(definition.getInstruction()).isEqualTo("Revised instruction.");
        assertThat(definition.getRuntimeLockedAt()).isEqualTo(LOCKED_AT);
        assertThat(response.instruction()).isEqualTo("Revised instruction.");
        assertThat(response.editability().runtimeFields()).isFalse();
    }

    @Test
    void aStandardTaskTypeRejectsAChangedScreenContractOrSection() {
        QuestionType definition = standardDefinition();
        stubUpdate(definition);
        UUID id = definition.getPublicId();

        assertThatThrownBy(() -> service.update(id, runtimeUpdate("MC_LISTENING_SINGLE_V1", 1, "SPEAKING"), admin))
                .isInstanceOf(TaskTypeStandardRuntimeImmutableException.class);
        assertThatThrownBy(() -> service.update(id, runtimeUpdate("READ_ALOUD_V1", 2, "SPEAKING"), admin))
                .isInstanceOf(TaskTypeStandardRuntimeImmutableException.class);
        assertThatThrownBy(() -> service.update(id, runtimeUpdate("READ_ALOUD_V1", 1, "READING"), admin))
                .isInstanceOf(TaskTypeStandardRuntimeImmutableException.class);

        assertThat(definition.getScreenKey()).isEqualTo("READ_ALOUD_V1");
        assertThat(definition.getSection()).isEqualTo(PteSection.SPEAKING);
        verify(repository, never()).save(any(QuestionType.class));
    }

    @Test
    void aStandardTaskTypeAcceptsUnchangedRuntimeValuesBecauseTheAdminFormResendsThem() {
        QuestionType definition = standardDefinition();
        stubUpdate(definition);

        QuestionTypeResponse response = service.update(definition.getPublicId(),
                runtimeUpdate("READ_ALOUD_V1", 1, " speaking "), admin);

        assertThat(response.instruction()).isEqualTo("New instruction.");
        assertThat(definition.getScreenKey()).isEqualTo("READ_ALOUD_V1");
        verify(contractResolver, never()).resolveActive(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void aCustomTaskTypeThatIsNotLockedStillRebindsItsScreen() {
        QuestionType definition = existingDefinition(null);
        stubUpdate(definition);
        when(contractResolver.resolveActive("CUSTOM_SCREEN_V1", 1)).thenReturn(contract(defaultRequirements()));

        service.update(definition.getPublicId(), runtimeUpdate("CUSTOM_SCREEN_V1", 1, "LISTENING"), admin);

        assertThat(definition.getScreenKey()).isEqualTo("CUSTOM_SCREEN_V1");
    }

    private void stubCreate(TaskRuntimeContractDescriptor contract) {
        when(contractResolver.resolveActive("CUSTOM_SCREEN_V1", 1)).thenReturn(contract);
        when(repository.findByTaskTypeKey(any())).thenReturn(Optional.empty());
        when(repository.findByNormalizedDisplayName(any())).thenReturn(Optional.empty());
        when(repository.save(any(QuestionType.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void stubUpdate(QuestionType definition) {
        when(repository.findByPublicIdAndDeletedFalse(definition.getPublicId())).thenReturn(Optional.of(definition));
        when(repository.findByNormalizedDisplayName(any())).thenReturn(Optional.empty());
        when(repository.save(any(QuestionType.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(contractResolver.resolveDynamic(definition)).thenReturn(contract(defaultRequirements()));
    }

    private QuestionType savedDefinition() {
        ArgumentCaptor<QuestionType> saved = ArgumentCaptor.forClass(QuestionType.class);
        verify(repository).save(saved.capture());
        return saved.getValue();
    }

    private static QuestionType existingDefinition(Instant runtimeLockedAt) {
        QuestionType definition = new QuestionType();
        definition.setPublicId(UUID.randomUUID());
        definition.setCode("CUSTOM_LISTEN");
        definition.setTaskTypeKey("CUSTOM_LISTEN");
        definition.setDisplayName("Custom listen");
        definition.setNormalizedDisplayName("custom listen");
        definition.setShortName("CL");
        definition.setSection(PteSection.LISTENING);
        definition.setInstruction("Original instruction.");
        definition.setRuntimeLockedAt(runtimeLockedAt);
        return definition;
    }

    private static QuestionType standardDefinition() {
        QuestionType definition = existingDefinition(null);
        definition.setCode("READ_ALOUD");
        definition.setTaskTypeKey("READ_ALOUD");
        definition.setDisplayName("Read Aloud");
        definition.setNormalizedDisplayName("read aloud");
        definition.setSection(PteSection.SPEAKING);
        definition.setScreenKey("READ_ALOUD_V1");
        definition.setRuntimeProfileVersion(1);
        return definition;
    }

    private static UpdateTaskTypeRequest runtimeUpdate(String screenKey, int contractVersion, String section) {
        return new UpdateTaskTypeRequest("Custom listen", "CL", 1, false, screenKey, contractVersion, section,
                "New instruction.");
    }

    private static CreateTaskTypeRequest createRequest(String instruction) {
        return new CreateTaskTypeRequest("CUSTOM_LISTEN", "Custom listen", "CL", "LISTENING",
                "CUSTOM_SCREEN_V1", 1, 1, false, instruction);
    }

    private static UpdateTaskTypeRequest updateRequest(String instruction) {
        return new UpdateTaskTypeRequest("Custom listen", "CL", 1, false, null, null, null, instruction);
    }

    private static TaskAuthoringRequirements defaultRequirements() {
        return new TaskAuthoringRequirements(true, false, false, false, false, false, false, false, false);
    }

    private static TaskRuntimeContractDescriptor contract(TaskAuthoringRequirements requirements) {
        return new TaskRuntimeContractDescriptor("CUSTOM_SCREEN_V1", 1,
                "CUSTOM_PROFILE", 1, "BEHAVIOR", "RENDERER", 1, "SCORING", 1, "AI", List.of("AUDIO_PLAYBACK"),
                "1.0.0", "AUTHORING", 1, requirements, "ACTIVE");
    }
}
