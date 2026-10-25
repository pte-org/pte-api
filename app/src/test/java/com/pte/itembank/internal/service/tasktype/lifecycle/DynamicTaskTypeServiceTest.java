package com.pte.itembank.internal.service.tasktype.lifecycle;

import com.pte.itembank.TaskAuthoringRequirements;
import com.pte.itembank.TaskRuntimeContractDescriptor;
import com.pte.itembank.domain.QuestionType;
import com.pte.itembank.dto.request.CreateTaskTypeRequest;
import com.pte.itembank.internal.repository.QuestionTypeRepository;
import com.pte.itembank.internal.service.tasktype.RuntimeContractResolver;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DynamicTaskTypeServiceTest {

    private final QuestionTypeRepository repository = mock(QuestionTypeRepository.class);
    private final RuntimeContractResolver contractResolver = mock(RuntimeContractResolver.class);
    private final CurrentUser admin = new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"));

    @Test
    void aCustomTaskTypeInheritsTheAuthoringRequirementsOfItsRuntimeContract() {
        TaskAuthoringRequirements requirements = new TaskAuthoringRequirements(
                true, false, true, true, true, false, true, true, false);
        TaskRuntimeContractDescriptor contract = new TaskRuntimeContractDescriptor("CUSTOM_SCREEN_V1", 1,
                "CUSTOM_PROFILE", 1, "BEHAVIOR", "RENDERER", 1, "SCORING", 1, "AI", List.of("AUDIO_PLAYBACK"),
                "1.0.0", "AUTHORING", 1, requirements, "ACTIVE");
        when(contractResolver.resolveActive("CUSTOM_SCREEN_V1", 1)).thenReturn(contract);
        when(repository.findByTaskTypeKey(any())).thenReturn(Optional.empty());
        when(repository.findByNormalizedDisplayName(any())).thenReturn(Optional.empty());
        when(repository.save(any(QuestionType.class))).thenAnswer(invocation -> invocation.getArgument(0));

        new DynamicTaskTypeService(repository, contractResolver, null, null).create(
                new CreateTaskTypeRequest("CUSTOM_LISTEN", "Custom listen", "CL", "LISTENING",
                        "CUSTOM_SCREEN_V1", 1, 1, false), admin);

        ArgumentCaptor<QuestionType> saved = ArgumentCaptor.forClass(QuestionType.class);
        verify(repository).save(saved.capture());
        QuestionType definition = saved.getValue();
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
}
