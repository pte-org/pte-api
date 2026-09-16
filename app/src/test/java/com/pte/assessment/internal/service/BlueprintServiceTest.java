package com.pte.assessment.internal.service;

import com.pte.assessment.domain.ExamBlueprint;
import com.pte.assessment.internal.dto.request.BlueprintItemRequest;
import com.pte.assessment.internal.dto.request.CreateBlueprintRequest;
import com.pte.assessment.internal.dto.response.BlueprintResponse;
import com.pte.assessment.internal.exception.BlueprintNotFoundException;
import com.pte.assessment.internal.exception.InvalidSectionException;
import com.pte.assessment.internal.repository.ExamBlueprintRepository;
import com.pte.itembank.ItembankService;
import com.pte.itembank.internal.exception.QuestionNotFoundException;
import com.pte.shared.security.CurrentUser;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the Phase 05/A boundary rule: {@code assessment} never touches
 * {@code itembank}'s repository — every referenced question's existence and
 * accessibility is checked through {@link ItembankService#get}.
 */
@ExtendWith(MockitoExtension.class)
class BlueprintServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();

    @Mock
    private ExamBlueprintRepository blueprintRepository;
    @Mock
    private ItembankService itembankService;

    private BlueprintService service;
    private CurrentUser caller;

    @BeforeEach
    void setUp() {
        service = new BlueprintService(blueprintRepository, itembankService, new AssessmentAccessPolicy());
        caller = new CurrentUser(UUID.randomUUID(), TENANT_ID, List.of("HOST_AUTHOR"));
    }

    @Test
    void create_delegatesEachItemsAccessibilityCheckToItembankService() {
        UUID questionId = UUID.randomUUID();
        when(blueprintRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        CreateBlueprintRequest request = new CreateBlueprintRequest(
                "Mock Test A", List.of(new BlueprintItemRequest(questionId, "READING", 0)));

        BlueprintResponse response = service.create(request, caller);

        verify(itembankService).get(questionId, caller);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).questionPublicId()).isEqualTo(questionId);
    }

    @Test
    void create_questionNotAccessible_propagatesItembankException_neverSaves() {
        UUID questionId = UUID.randomUUID();
        doThrow(new QuestionNotFoundException()).when(itembankService).get(eq(questionId), any());
        CreateBlueprintRequest request = new CreateBlueprintRequest(
                "Mock Test A", List.of(new BlueprintItemRequest(questionId, "READING", 0)));

        assertThatThrownBy(() -> service.create(request, caller)).isInstanceOf(QuestionNotFoundException.class);
        verify(blueprintRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void create_invalidSection_throws() {
        UUID questionId = UUID.randomUUID();
        CreateBlueprintRequest request = new CreateBlueprintRequest(
                "Mock Test A", List.of(new BlueprintItemRequest(questionId, "NOT_A_SECTION", 0)));

        assertThatThrownBy(() -> service.create(request, caller)).isInstanceOf(InvalidSectionException.class);
    }

    @Test
    void get_crossTenantBlueprint_throwsNotFound() {
        UUID publicId = UUID.randomUUID();
        ExamBlueprint blueprint = new ExamBlueprint();
        blueprint.setTenantId(UUID.randomUUID());
        when(blueprintRepository.findWithItemsByPublicId(publicId)).thenReturn(Optional.of(blueprint));

        assertThatThrownBy(() -> service.get(publicId, caller)).isInstanceOf(BlueprintNotFoundException.class);
    }

    @Test
    void get_unknownBlueprint_throwsNotFound() {
        UUID publicId = UUID.randomUUID();
        when(blueprintRepository.findWithItemsByPublicId(publicId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(publicId, caller)).isInstanceOf(BlueprintNotFoundException.class);
    }
}
