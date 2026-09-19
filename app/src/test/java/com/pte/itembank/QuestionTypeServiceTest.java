package com.pte.itembank;

import com.pte.itembank.domain.QuestionTypeDefinition;
import com.pte.itembank.dto.request.ImportQuestionTypesFromScoreTemplateRequest;
import com.pte.itembank.dto.request.UpdateQuestionTypeRequest;
import com.pte.itembank.dto.response.QuestionTypeResponse;
import com.pte.itembank.internal.exception.QuestionTypeImportException;
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
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

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
        assertThat(result.requiresPromptText()).isFalse();
    }

    @Test
    void importFromScoreTemplate_createsMissingTypeFromUiSourceRow() {
        when(repository.findMaxDisplayOrder()).thenReturn(0);
        QuestionTypeDefinition persisted = definition("READ_ALOUD", true);
        when(repository.findByCodeAndDeletedFalse("READ_ALOUD"))
                .thenReturn(Optional.empty(), Optional.of(persisted));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<QuestionTypeResponse> result = service.importFromScoreTemplate(
                new ImportQuestionTypesFromScoreTemplateRequest(List.of(
                        new ImportQuestionTypesFromScoreTemplateRequest.Item("READ_ALOUD", "SPEAKING", 1))));

        var captor = forClass(QuestionTypeDefinition.class);
        verify(repository).save(captor.capture());
        assertThat(result).extracting(QuestionTypeResponse::code).containsExactly("READ_ALOUD");
        assertThat(captor.getValue().getDisplayName()).isEqualTo("Read Aloud");
        assertThat(captor.getValue().isRequiresPromptText()).isTrue();
        assertThat(captor.getValue().getDisplayOrder()).isEqualTo(1);
    }

    @Test
    void importFromScoreTemplate_rejectsUnknownTaskType() {
        when(repository.findMaxDisplayOrder()).thenReturn(0);

        assertThatThrownBy(() -> service.importFromScoreTemplate(
                new ImportQuestionTypesFromScoreTemplateRequest(List.of(
                        new ImportQuestionTypesFromScoreTemplateRequest.Item("UNKNOWN", "READING", 1)))))
                .isInstanceOf(QuestionTypeImportException.class);
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
