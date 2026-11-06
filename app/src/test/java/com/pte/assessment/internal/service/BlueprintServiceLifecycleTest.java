package com.pte.assessment.internal.service;

import com.pte.assessment.ExamDefinitionService;
import com.pte.assessment.domain.BlueprintItem;
import com.pte.assessment.domain.ExamBlueprint;
import com.pte.assessment.domain.enums.BlueprintStatus;
import com.pte.assessment.dto.request.BlueprintItemRequest;
import com.pte.assessment.dto.request.CreateBlueprintRequest;
import com.pte.assessment.dto.request.RejectBlueprintRequest;
import com.pte.assessment.dto.response.ExamDefinitionItemResponse;
import com.pte.assessment.dto.response.ExamDefinitionResponse;
import com.pte.assessment.dto.response.SnapshotResponse;
import com.pte.assessment.internal.constant.AssessmentConstants;
import com.pte.assessment.internal.dto.response.BlueprintResponse;
import com.pte.assessment.internal.exception.BlueprintNotFoundException;
import com.pte.assessment.internal.exception.BlueprintValidationException;
import com.pte.assessment.internal.exception.BlueprintVersionConflictException;
import com.pte.assessment.internal.repository.ExamBlueprintRepository;
import com.pte.itembank.ItembankService;
import com.pte.itembank.domain.enums.PteSection;
import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.itembank.dto.response.QuestionFreezeView;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Authoring lifecycle of a blueprint: create, update, submit, approve, reject and scoping. */
@ExtendWith(MockitoExtension.class)
class BlueprintServiceLifecycleTest {

    @Mock
    private ExamBlueprintRepository blueprintRepository;
    @Mock
    private ItembankService itembankService;
    @Mock
    private ExamDefinitionService examDefinitionService;
    @Mock
    private SnapshotPublishService snapshotPublishService;

    private BlueprintService service;
    private CurrentUser author;
    private CurrentUser manager;
    private CurrentUser operator;

    @BeforeEach
    void setUp() {
        service = new BlueprintService(blueprintRepository, new AssessmentAccessPolicy(), itembankService,
                examDefinitionService, snapshotPublishService, null);
        author = new CurrentUser(UUID.randomUUID(), null, List.of("ACADEMIC_STAFF"));
        manager = new CurrentUser(UUID.randomUUID(), null, List.of("ACADEMIC_MANAGER"));
        operator = new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_MANAGER"));
    }

    private ExamBlueprint draftBy(CurrentUser owner) {
        ExamBlueprint blueprint = new ExamBlueprint();
        blueprint.setPublicId(UUID.randomUUID());
        blueprint.setName("Draft");
        blueprint.setAuthorUserPublicId(owner.userId());
        return blueprint;
    }

    private void stubTemplate(int min, int max) {
        ExamDefinitionItemResponse item = new ExamDefinitionItemResponse("READ_ALOUD", "SPEAKING", 1, min, max,
                35, 40, "AI", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        when(examDefinitionService.getActive()).thenReturn(new ExamDefinitionResponse(UUID.randomUUID(),
                "PTE_CORE", 1, "Core", "ACTIVE", List.of(item)));
    }

    private QuestionFreezeView readAloud(UUID questionId) {
        return new QuestionFreezeView(questionId, PteTaskType.READ_ALOUD, "t", "p", null, null, null, null, null,
                null, List.of(), "READ_ALOUD", "SPEAKING", null, "Read Aloud");
    }

    private void addItem(ExamBlueprint blueprint, UUID questionId) {
        BlueprintItem item = new BlueprintItem();
        item.setQuestionPublicId(questionId);
        item.setSection(PteSection.SPEAKING);
        item.setOrderIndex(blueprint.getItems().size());
        blueprint.addItem(item);
    }

    @Test
    void create_callerWithoutDraftCapability_isDenied() {
        CreateBlueprintRequest request = new CreateBlueprintRequest("Mock 1",
                List.of(new BlueprintItemRequest(UUID.randomUUID(), null, null)), null);

        assertThatThrownBy(() -> service.create(request, operator)).isInstanceOf(AccessDeniedException.class);
        verify(blueprintRepository, never()).save(any());
    }

    @Test
    void create_sameQuestionTwice_isRejected() {
        stubTemplate(1, 3);
        UUID questionId = UUID.randomUUID();
        CreateBlueprintRequest request = new CreateBlueprintRequest("Mock 1", List.of(
                new BlueprintItemRequest(questionId, null, null),
                new BlueprintItemRequest(questionId, null, null)), null);
        when(itembankService.freeze(questionId)).thenReturn(readAloud(questionId));

        assertThatThrownBy(() -> service.create(request, author))
                .isInstanceOf(BlueprintValidationException.class)
                .hasMessage(AssessmentConstants.BLUEPRINT_DUPLICATE_QUESTION);
    }

    @Test
    void create_questionOutsideTemplate_isRejected() {
        stubTemplate(1, 3);
        UUID questionId = UUID.randomUUID();
        QuestionFreezeView essay = new QuestionFreezeView(questionId, PteTaskType.WRITE_ESSAY, "t", "p", null, null,
                null, null, null, null, List.of(), "WRITE_ESSAY", "WRITING", null, "Essay");
        when(itembankService.freeze(questionId)).thenReturn(essay);
        CreateBlueprintRequest request = new CreateBlueprintRequest("Mock 1",
                List.of(new BlueprintItemRequest(questionId, null, null)), null);

        assertThatThrownBy(() -> service.create(request, author))
                .isInstanceOf(BlueprintValidationException.class)
                .hasMessage(AssessmentConstants.BLUEPRINT_ITEM_INVALID);
    }

    @Test
    void create_wrongSectionClaimed_isRejected() {
        stubTemplate(1, 3);
        UUID questionId = UUID.randomUUID();
        when(itembankService.freeze(questionId)).thenReturn(readAloud(questionId));
        CreateBlueprintRequest request = new CreateBlueprintRequest("Mock 1",
                List.of(new BlueprintItemRequest(questionId, "READING", null)), null);

        assertThatThrownBy(() -> service.create(request, author))
                .isInstanceOf(BlueprintValidationException.class)
                .hasMessage(AssessmentConstants.BLUEPRINT_ITEM_INVALID);
    }

    @Test
    void create_validRequest_savesTrimmedNameAsDraftOwnedByCaller() {
        stubTemplate(1, 3);
        UUID questionId = UUID.randomUUID();
        when(itembankService.freeze(questionId)).thenReturn(readAloud(questionId));
        when(blueprintRepository.save(any(ExamBlueprint.class))).thenAnswer(call -> call.getArgument(0));
        CreateBlueprintRequest request = new CreateBlueprintRequest("  Mock 1  ",
                List.of(new BlueprintItemRequest(questionId, "SPEAKING", null)), null);

        BlueprintResponse response = service.create(request, author);

        assertThat(response.name()).isEqualTo("Mock 1");
        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).orderIndex()).isZero();
    }

    @Test
    void update_staleVersion_throwsConflict() {
        ExamBlueprint blueprint = draftBy(author);
        when(blueprintRepository.findWithItemsByPublicId(blueprint.getPublicId())).thenReturn(Optional.of(blueprint));
        CreateBlueprintRequest request = new CreateBlueprintRequest("New",
                List.of(new BlueprintItemRequest(UUID.randomUUID(), null, null)), blueprint.getVersion() + 1);

        assertThatThrownBy(() -> service.update(blueprint.getPublicId(), request, author))
                .isInstanceOf(BlueprintVersionConflictException.class);
    }

    @Test
    void update_blueprintAlreadySubmitted_isRejected() {
        ExamBlueprint blueprint = draftBy(author);
        blueprint.setStatus(BlueprintStatus.PENDING_APPROVAL);
        when(blueprintRepository.findWithItemsByPublicId(blueprint.getPublicId())).thenReturn(Optional.of(blueprint));
        CreateBlueprintRequest request = new CreateBlueprintRequest("New",
                List.of(new BlueprintItemRequest(UUID.randomUUID(), null, null)), null);

        assertThatThrownBy(() -> service.update(blueprint.getPublicId(), request, author))
                .isInstanceOf(BlueprintValidationException.class)
                .hasMessage(AssessmentConstants.BLUEPRINT_STATUS_INVALID);
    }

    @Test
    void update_draftOfAnotherAuthor_looksLikeNotFound() {
        ExamBlueprint blueprint = draftBy(manager);
        when(blueprintRepository.findWithItemsByPublicId(blueprint.getPublicId())).thenReturn(Optional.of(blueprint));
        CreateBlueprintRequest request = new CreateBlueprintRequest("New",
                List.of(new BlueprintItemRequest(UUID.randomUUID(), null, null)), null);

        assertThatThrownBy(() -> service.update(blueprint.getPublicId(), request, author))
                .isInstanceOf(BlueprintNotFoundException.class);
    }

    @Test
    void update_withoutVersion_replacesNameAndClearsRejectionReason() {
        stubTemplate(1, 3);
        ExamBlueprint blueprint = draftBy(author);
        blueprint.setRejectionReason("Needs more reading items");
        UUID questionId = UUID.randomUUID();
        when(blueprintRepository.findWithItemsByPublicId(blueprint.getPublicId())).thenReturn(Optional.of(blueprint));
        when(itembankService.freeze(questionId)).thenReturn(readAloud(questionId));
        CreateBlueprintRequest request = new CreateBlueprintRequest(" Renamed ",
                List.of(new BlueprintItemRequest(questionId, null, null)), null);

        BlueprintResponse response = service.update(blueprint.getPublicId(), request, author);

        assertThat(response.name()).isEqualTo("Renamed");
        assertThat(response.rejectionReason()).isNull();
        assertThat(response.items()).hasSize(1);
    }

    @Test
    void submitApproval_emptyBlueprint_isRejected() {
        ExamBlueprint blueprint = draftBy(author);
        when(blueprintRepository.findWithItemsByPublicId(blueprint.getPublicId())).thenReturn(Optional.of(blueprint));

        assertThatThrownBy(() -> service.submitApproval(blueprint.getPublicId(), author))
                .isInstanceOf(BlueprintValidationException.class)
                .hasMessage(AssessmentConstants.BLUEPRINT_ITEMS_REQUIRED);
    }

    @Test
    void submitApproval_countBelowTemplateMinimum_isRejected() {
        stubTemplate(2, 3);
        ExamBlueprint blueprint = draftBy(author);
        UUID questionId = UUID.randomUUID();
        addItem(blueprint, questionId);
        when(blueprintRepository.findWithItemsByPublicId(blueprint.getPublicId())).thenReturn(Optional.of(blueprint));
        when(itembankService.freeze(questionId)).thenReturn(readAloud(questionId));

        assertThatThrownBy(() -> service.submitApproval(blueprint.getPublicId(), author))
                .isInstanceOf(BlueprintValidationException.class)
                .hasMessage(AssessmentConstants.BLUEPRINT_TEMPLATE_COMPLIANCE_INVALID);
    }

    @Test
    void submitApproval_countAboveTemplateMaximum_isRejected() {
        stubTemplate(1, 1);
        ExamBlueprint blueprint = draftBy(author);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        addItem(blueprint, first);
        addItem(blueprint, second);
        when(blueprintRepository.findWithItemsByPublicId(blueprint.getPublicId())).thenReturn(Optional.of(blueprint));
        when(itembankService.freeze(first)).thenReturn(readAloud(first));
        when(itembankService.freeze(second)).thenReturn(readAloud(second));

        assertThatThrownBy(() -> service.submitApproval(blueprint.getPublicId(), author))
                .isInstanceOf(BlueprintValidationException.class)
                .hasMessage(AssessmentConstants.BLUEPRINT_TEMPLATE_COMPLIANCE_INVALID);
    }

    @Test
    void submitApproval_countAtTemplateLimit_movesToPendingApproval() {
        stubTemplate(1, 1);
        ExamBlueprint blueprint = draftBy(author);
        UUID questionId = UUID.randomUUID();
        addItem(blueprint, questionId);
        when(blueprintRepository.findWithItemsByPublicId(blueprint.getPublicId())).thenReturn(Optional.of(blueprint));
        when(itembankService.freeze(questionId)).thenReturn(readAloud(questionId));

        BlueprintResponse response = service.submitApproval(blueprint.getPublicId(), author);

        assertThat(response.status()).isEqualTo("PENDING_APPROVAL");
    }

    @Test
    void reject_byAuthorWhoIsManager_isForbiddenAsSelfApproval() {
        ExamBlueprint blueprint = draftBy(manager);
        blueprint.setStatus(BlueprintStatus.PENDING_APPROVAL);
        when(blueprintRepository.findWithItemsByPublicId(blueprint.getPublicId())).thenReturn(Optional.of(blueprint));

        assertThatThrownBy(() -> service.reject(blueprint.getPublicId(), new RejectBlueprintRequest("no"), manager))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage(AssessmentConstants.ACADEMIC_SELF_APPROVAL_FORBIDDEN);
    }

    @Test
    void reject_byStaffWithoutReviewRight_isDenied() {
        ExamBlueprint blueprint = draftBy(manager);
        blueprint.setStatus(BlueprintStatus.PENDING_APPROVAL);
        when(blueprintRepository.findWithItemsByPublicId(blueprint.getPublicId())).thenReturn(Optional.of(blueprint));

        assertThatThrownBy(() -> service.reject(blueprint.getPublicId(), new RejectBlueprintRequest("no"), author))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage(AssessmentConstants.ACADEMIC_REVIEW_REQUIRED);
    }

    @Test
    void reject_blueprintStillDraft_isRejected() {
        ExamBlueprint blueprint = draftBy(author);
        when(blueprintRepository.findWithItemsByPublicId(blueprint.getPublicId())).thenReturn(Optional.of(blueprint));

        assertThatThrownBy(() -> service.reject(blueprint.getPublicId(), new RejectBlueprintRequest("no"), manager))
                .isInstanceOf(BlueprintValidationException.class)
                .hasMessage(AssessmentConstants.BLUEPRINT_STATUS_INVALID);
    }

    @Test
    void reject_pendingBlueprintByAnotherManager_returnsToDraftWithTrimmedReason() {
        ExamBlueprint blueprint = draftBy(author);
        blueprint.setStatus(BlueprintStatus.PENDING_APPROVAL);
        when(blueprintRepository.findWithItemsByPublicId(blueprint.getPublicId())).thenReturn(Optional.of(blueprint));

        BlueprintResponse response = service.reject(blueprint.getPublicId(),
                new RejectBlueprintRequest("  Add a listening item  "), manager);

        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.rejectionReason()).isEqualTo("Add a listening item");
    }

    @Test
    void approve_unknownBlueprint_throwsNotFound() {
        UUID publicId = UUID.randomUUID();
        when(blueprintRepository.findWithItemsByPublicId(publicId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(publicId, manager)).isInstanceOf(BlueprintNotFoundException.class);
    }

    @Test
    void approve_blueprintNotPending_isRejectedAndNothingPublished() {
        ExamBlueprint blueprint = draftBy(author);
        when(blueprintRepository.findWithItemsByPublicId(blueprint.getPublicId())).thenReturn(Optional.of(blueprint));

        assertThatThrownBy(() -> service.approve(blueprint.getPublicId(), manager))
                .isInstanceOf(BlueprintValidationException.class);
        verify(snapshotPublishService, never()).publish(any(), any());
    }

    @Test
    void approve_pendingBlueprintThatMatchesTemplate_publishesSnapshot() {
        stubTemplate(1, 1);
        ExamBlueprint blueprint = draftBy(author);
        blueprint.setStatus(BlueprintStatus.PENDING_APPROVAL);
        UUID questionId = UUID.randomUUID();
        addItem(blueprint, questionId);
        SnapshotResponse snapshot = org.mockito.Mockito.mock(SnapshotResponse.class);
        when(blueprintRepository.findWithItemsByPublicId(blueprint.getPublicId())).thenReturn(Optional.of(blueprint));
        when(itembankService.freeze(questionId)).thenReturn(readAloud(questionId));
        when(snapshotPublishService.publish(blueprint.getPublicId(), manager)).thenReturn(snapshot);

        assertThat(service.approve(blueprint.getPublicId(), manager)).isSameAs(snapshot);
    }

    @Test
    void get_blueprintOfAnotherTenant_looksLikeNotFound() {
        ExamBlueprint blueprint = draftBy(author);
        blueprint.setTenantId(UUID.randomUUID());
        CurrentUser tenantUser = new CurrentUser(UUID.randomUUID(), UUID.randomUUID(), List.of("HOST"));
        when(blueprintRepository.findWithItemsByPublicId(blueprint.getPublicId())).thenReturn(Optional.of(blueprint));

        assertThatThrownBy(() -> service.get(blueprint.getPublicId(), tenantUser))
                .isInstanceOf(BlueprintNotFoundException.class);
    }

    @Test
    void list_tenantCaller_readsOnlyOwnTenant() {
        UUID tenantId = UUID.randomUUID();
        CurrentUser tenantUser = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST"));
        ExamBlueprint blueprint = draftBy(author);
        blueprint.setTenantId(tenantId);
        when(blueprintRepository.findByTenantId(tenantId)).thenReturn(List.of(blueprint));

        assertThat(service.list(tenantUser)).hasSize(1);
        verify(blueprintRepository, never()).findByTenantIdIsNull();
    }
}
