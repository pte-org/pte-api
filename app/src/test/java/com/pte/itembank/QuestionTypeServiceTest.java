package com.pte.itembank;

import com.pte.itembank.domain.QuestionTypeDefinition;
import com.pte.itembank.domain.enums.PteSection;
import com.pte.itembank.dto.request.CreateQuestionTypeRequest;
import com.pte.itembank.dto.request.UpdateQuestionTypeRequest;
import com.pte.itembank.dto.response.QuestionTypeResponse;
import com.pte.itembank.dto.response.SupportedQuestionTypeResponse;
import com.pte.itembank.internal.repository.QuestionTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionTypeServiceTest {

    @Mock
    private QuestionTypeRepository repository;

    private QuestionTypeService service;

    @BeforeEach
    void setUp() {
        service = new QuestionTypeService(repository);
    }

    @Test
    void listActiveOnly_readsTheActiveCatalogQuery() {
        QuestionTypeDefinition definition = definition("READ_ALOUD", true);
        when(repository.findAllByDeletedFalseAndActiveTrueOrderByDisplayOrderAscCodeAsc())
                .thenReturn(List.of(definition));

        List<QuestionTypeResponse> result = service.list(true);

        assertThat(result).extracting(QuestionTypeResponse::code).containsExactly("READ_ALOUD");
    }

    @Test
    void listSupported_readsTheStandardTaskVocabularyFromTheBackend() {
        List<SupportedQuestionTypeResponse> result = service.listSupported();

        assertThat(result).hasSize(23);
        assertThat(result).extracting(SupportedQuestionTypeResponse::code)
                .contains("READ_ALOUD", "WRITE_FROM_DICTATION");
        assertThat(result.stream().filter(type -> type.code().equals("READ_ALOUD")).findFirst())
                .get()
                .satisfies(type -> {
                    assertThat(type.section()).isEqualTo(PteSection.SPEAKING);
                    assertThat(type.scored()).isTrue();
                });
    }

    @Test
    void update_persistsPresentationAndAuthoringMetadata_withoutChangingStableCode() {
        UUID publicId = UUID.randomUUID();
        QuestionTypeDefinition definition = definition("READ_ALOUD", true);
        definition.setPublicId(publicId);
        when(repository.findByPublicIdAndDeletedFalse(publicId)).thenReturn(java.util.Optional.of(definition));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateQuestionTypeRequest request = new UpdateQuestionTypeRequest(
                "Read aloud updated", "RA+", 42, false,
                false, false, false, false, false, false, false, false);

        QuestionTypeResponse result = service.update(publicId, request);

        assertThat(result.code()).isEqualTo("READ_ALOUD");
        assertThat(result.displayName()).isEqualTo("Read aloud updated");
        assertThat(result.shortName()).isEqualTo("RA+");
        assertThat(result.displayOrder()).isEqualTo(42);
        assertThat(result.active()).isFalse();
        assertThat(result.requiresPromptText()).isTrue();
    }

    @Test
    void create_rejectsLegacyFillBlanksAliases() {
        assertThatThrownBy(() -> service.create(new CreateQuestionTypeRequest(
                "FILL_BLANKS_READING", "Fill in the blanks", "FIB", "READING", 1, true)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("INVALID_QUESTION_TYPE");
    }

    @Test
    void create_derivesCanonicalMetadataFromTheStandardTaskType() {
        when(repository.findByCode("PERSONAL_INTRODUCTION")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        QuestionTypeResponse result = service.create(new CreateQuestionTypeRequest(
                " personal_introduction ", "Personal Introduction", "PI", "SPEAKING", 23, true));

        assertThat(result.code()).isEqualTo("PERSONAL_INTRODUCTION");
        assertThat(result.section()).isEqualTo("SPEAKING");
        assertThat(result.scored()).isFalse();
        assertThat(result.requiresPromptText()).isTrue();
    }

    @Test
    void create_rejectsASectionThatDoesNotMatchTheStandardTaskType() {
        assertThatThrownBy(() -> service.create(new CreateQuestionTypeRequest(
                "READ_ALOUD", "Read aloud", "RA", "READING", 1, true)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("INVALID_QUESTION_TYPE");
    }

    @Test
    void create_rejectsAnUnknownSectionWithTheCatalogValidationError() {
        assertThatThrownBy(() -> service.create(new CreateQuestionTypeRequest(
                "READ_ALOUD", "Read aloud", "RA", "NOT_A_SECTION", 1, true)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("INVALID_QUESTION_TYPE");
    }

    @Test
    void findDefinitionByCode_keepsDeletedMetadataAvailableToRuntimeValidation() {
        QuestionTypeDefinition definition = definition("READ_ALOUD", false);
        definition.setDeleted(true);
        when(repository.findByCode("READ_ALOUD")).thenReturn(Optional.of(definition));

        assertThat(service.findDefinitionByCode("READ_ALOUD")).containsSame(definition);
    }

    @Test
    void findDefinitionByCode_readsTheKnownLegacyAliasAsCanonical() {
        QuestionTypeDefinition definition = definition("FILL_IN_THE_BLANKS_DRAG_AND_DROP", true);
        when(repository.findByCode("FILL_IN_THE_BLANKS_DRAG_AND_DROP")).thenReturn(Optional.of(definition));

        assertThat(service.findDefinitionByCode("FILL_BLANKS_READING")).containsSame(definition);
    }

    @Test
    void delete_softDeletesTheCatalogRowToPreserveExistingQuestionReferences() {
        UUID publicId = UUID.randomUUID();
        QuestionTypeDefinition definition = definition("READ_ALOUD", true);
        definition.setPublicId(publicId);
        when(repository.findByPublicIdAndDeletedFalse(publicId)).thenReturn(Optional.of(definition));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.delete(publicId);

        assertThat(definition.isDeleted()).isTrue();
        assertThat(definition.isActive()).isFalse();
        verify(repository).save(definition);
    }

    private QuestionTypeDefinition definition(String code, boolean active) {
        QuestionTypeDefinition definition = new QuestionTypeDefinition();
        definition.setPublicId(UUID.randomUUID());
        definition.setCode(code);
        definition.setDisplayName(code);
        definition.setShortName("RA");
        definition.setActive(active);
        definition.setDisplayOrder(1);
        definition.setSection(com.pte.itembank.domain.enums.PteSection.SPEAKING);
        return definition;
    }
}
