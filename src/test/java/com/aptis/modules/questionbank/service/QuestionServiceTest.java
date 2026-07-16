package com.aptis.modules.questionbank.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.aptis.common.exception.ApiException;
import com.aptis.common.exception.ErrorCode;
import com.aptis.common.security.JwtPrincipal;
import com.aptis.modules.asset.interfaces.AssetOperations;
import com.aptis.modules.examoperations.repository.ExamQuestionRepository;
import com.aptis.modules.questionbank.domain.Question;
import com.aptis.modules.questionbank.dto.request.CreateQuestionRequest;
import com.aptis.modules.questionbank.dto.request.UpdateQuestionRequest;
import com.aptis.modules.questionbank.repository.QuestionRepository;
import com.aptis.modules.storage.interfaces.StorageService;

@ExtendWith(MockitoExtension.class)
@DisplayName("QuestionService PTE Task Type Validation Tests")
class QuestionServiceTest {

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private ExamQuestionRepository examQuestionRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private AssetOperations assetOperations;

    @Mock
    private QuestionActorResolver actorResolver;

    private QuestionService service;
    private JwtPrincipal hostPrincipal;

    @BeforeEach
    void setUp() {
        service = new QuestionService(questionRepository, examQuestionRepository, storageService, assetOperations, actorResolver);
        hostPrincipal = new JwtPrincipal(1L, "HOST", "HOST", 100L);
    }

    @Nested
    @DisplayName("createQuestion Task Type Validation Tests")
    class CreateQuestionTaskTypeValidationTests {

        @Test
        @DisplayName("OBJECTIVE task type without correctAnswers throws VALIDATION_ERROR")
        void objectiveTypeWithoutCorrectAnswersThrows() {
            CreateQuestionRequest request = new CreateQuestionRequest(
                    "READING",
                    1,
                    "MULTIPLE_CHOICE",
                    "Which is correct?",
                    "instruction",
                    1.0f,
                    "explanation",
                    null,
                    null,
                    null,
                    List.of(),
                    "B1",
                    List.of(),
                    List.of("Option A", "Option B"),
                    null,
                    "READING_FILL_IN_THE_BLANKS",
                    null,
                    null,
                    null
            );

            assertThatThrownBy(() -> service.createQuestion(request, hostPrincipal))
                    .isInstanceOf(ApiException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);
        }

        @Test
        @DisplayName("OBJECTIVE task type with empty correctAnswers throws VALIDATION_ERROR")
        void objectiveTypeWithEmptyCorrectAnswersThrows() {
            CreateQuestionRequest request = new CreateQuestionRequest(
                    "READING",
                    1,
                    "MULTIPLE_CHOICE",
                    "Which is correct?",
                    "instruction",
                    1.0f,
                    "explanation",
                    null,
                    null,
                    null,
                    List.of(),
                    "B1",
                    List.of(),
                    List.of("Option A", "Option B"),
                    List.of(),
                    "MULTIPLE_CHOICE_READING_SINGLE_ANSWER",
                    null,
                    null,
                    null
            );

            assertThatThrownBy(() -> service.createQuestion(request, hostPrincipal))
                    .isInstanceOf(ApiException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);
        }

        @Test
        @DisplayName("OBJECTIVE task type with correctAnswers succeeds")
        void objectiveTypeWithCorrectAnswersSucceeds() {
            CreateQuestionRequest request = new CreateQuestionRequest(
                    "READING",
                    1,
                    "MULTIPLE_CHOICE",
                    "Which is correct?",
                    "instruction",
                    1.0f,
                    "explanation",
                    null,
                    null,
                    null,
                    List.of(),
                    "B1",
                    List.of(),
                    List.of("Option A", "Option B"),
                    List.of("Option A"),
                    "MULTIPLE_CHOICE_READING_SINGLE_ANSWER",
                    null,
                    null,
                    null
            );

            UUID creatorId = UUID.randomUUID();
            UUID tenantId = UUID.randomUUID();
            when(actorResolver.resolveActorPublicId(hostPrincipal)).thenReturn(creatorId);
            when(actorResolver.isHost(hostPrincipal)).thenReturn(true);
            when(actorResolver.resolveCallerTenantId(hostPrincipal)).thenReturn(tenantId);
            when(questionRepository.save(any(Question.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Should not throw
            service.createQuestion(request, hostPrincipal);
        }

        @Test
        @DisplayName("AI_SCORED task type without correctAnswers succeeds")
        void aiScoredTypeWithoutCorrectAnswersSucceeds() {
            CreateQuestionRequest request = new CreateQuestionRequest(
                    "SPEAKING",
                    1,
                    "AUDIO_RECORD",
                    "Describe the image",
                    "instruction",
                    1.0f,
                    "explanation",
                    null,
                    null,
                    null,
                    List.of(),
                    "B1",
                    List.of(),
                    null,
                    null,
                    "DESCRIBE_IMAGE",
                    "Model description",
                    null,
                    null
            );

            UUID creatorId = UUID.randomUUID();
            UUID tenantId = UUID.randomUUID();
            when(actorResolver.resolveActorPublicId(hostPrincipal)).thenReturn(creatorId);
            when(actorResolver.isHost(hostPrincipal)).thenReturn(true);
            when(actorResolver.resolveCallerTenantId(hostPrincipal)).thenReturn(tenantId);
            when(questionRepository.save(any(Question.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Should not throw
            service.createQuestion(request, hostPrincipal);
        }

        @Test
        @DisplayName("UNSCORED task type without correctAnswers succeeds")
        void unscoredTypeWithoutCorrectAnswersSucceeds() {
            CreateQuestionRequest request = new CreateQuestionRequest(
                    "SPEAKING",
                    1,
                    "AUDIO_RECORD",
                    "Introduce yourself",
                    "instruction",
                    1.0f,
                    "explanation",
                    null,
                    null,
                    null,
                    List.of(),
                    "B1",
                    List.of(),
                    null,
                    null,
                    "PERSONAL_INTRODUCTION",
                    null,
                    null,
                    null
            );

            UUID creatorId = UUID.randomUUID();
            UUID tenantId = UUID.randomUUID();
            when(actorResolver.resolveActorPublicId(hostPrincipal)).thenReturn(creatorId);
            when(actorResolver.isHost(hostPrincipal)).thenReturn(true);
            when(actorResolver.resolveCallerTenantId(hostPrincipal)).thenReturn(tenantId);
            when(questionRepository.save(any(Question.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Should not throw
            service.createQuestion(request, hostPrincipal);
        }
    }

    @Nested
    @DisplayName("updateQuestion Task Type Validation Tests")
    class UpdateQuestionTaskTypeValidationTests {

        @Test
        @DisplayName("updateQuestion: OBJECTIVE without correctAnswers throws VALIDATION_ERROR")
        void updateObjectiveWithoutCorrectAnswersThrows() {
            UpdateQuestionRequest request = new UpdateQuestionRequest(
                    "READING",
                    1,
                    "MULTIPLE_CHOICE",
                    "Updated content",
                    "instruction",
                    1.0f,
                    "explanation",
                    null,
                    null,
                    null,
                    List.of(),
                    "B1",
                    List.of(),
                    List.of("Option A", "Option B"),
                    null,
                    "READING_WRITING_FILL_IN_THE_BLANKS",
                    null,
                    null,
                    null
            );

            assertThatThrownBy(() -> service.updateQuestion(UUID.randomUUID(), request, hostPrincipal))
                    .isInstanceOf(ApiException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);
        }

        @Test
        @DisplayName("updateQuestion: AI_SCORED without correctAnswers succeeds")
        void updateAiScoredWithoutCorrectAnswersSucceeds() {
            UpdateQuestionRequest request = new UpdateQuestionRequest(
                    "WRITING",
                    1,
                    "AUDIO_RECORD",
                    "Updated essay prompt",
                    "instruction",
                    1.0f,
                    "explanation",
                    null,
                    null,
                    null,
                    List.of(),
                    "B1",
                    List.of(),
                    null,
                    null,
                    "SUMMARIZE_WRITTEN_TEXT",
                    "Updated model answer",
                    null,
                    null
            );

            Question existing = mock(Question.class);
            UUID tenantId = UUID.randomUUID();
            when(existing.getIsImmutable()).thenReturn(false);
            when(existing.getSource()).thenReturn(com.aptis.modules.questionbank.domain.enums.QuestionSource.HOST);
            when(existing.getTenantId()).thenReturn(tenantId);
            when(questionRepository.findByPublicId(any())).thenReturn(java.util.Optional.of(existing));
            when(questionRepository.save(any(Question.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(actorResolver.isHost(hostPrincipal)).thenReturn(true);
            when(actorResolver.resolveCallerTenantId(hostPrincipal)).thenReturn(tenantId);

            // Should not throw
            service.updateQuestion(UUID.randomUUID(), request, hostPrincipal);
        }
    }

}
