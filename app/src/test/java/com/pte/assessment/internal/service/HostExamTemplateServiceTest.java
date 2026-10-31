package com.pte.assessment.internal.service;

import com.pte.assessment.domain.ExamDefinition;
import com.pte.assessment.domain.ExamDefinitionItem;
import com.pte.assessment.domain.enums.ExamDefinitionStatus;
import com.pte.assessment.domain.enums.ScoringMethod;
import com.pte.assessment.domain.enums.TemplatePolicy;
import com.pte.assessment.dto.request.CreateHostExamTemplateRequest;
import com.pte.assessment.dto.request.ExamDefinitionItemRequest;
import com.pte.assessment.dto.request.ReplaceExamDefinitionItemsRequest;
import com.pte.assessment.dto.response.ExamDefinitionResponse;
import com.pte.assessment.internal.exception.ExamDefinitionNotDraftException;
import com.pte.assessment.internal.exception.ExamDefinitionNotFoundException;
import com.pte.assessment.internal.exception.ExamDefinitionValidationException;
import com.pte.assessment.internal.exception.NoActiveExamDefinitionException;
import com.pte.assessment.internal.repository.ExamDefinitionRepository;
import com.pte.assessment.internal.service.definition.HostExamTemplateService;
import com.pte.itembank.TaskRuntimeProfileDescriptor;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HostExamTemplateServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID OTHER_TENANT = UUID.randomUUID();

    @Mock
    private ExamDefinitionRepository repository;

    private HostExamTemplateService service;
    private CurrentUser host;

    @BeforeEach
    void setUp() {
        service = new HostExamTemplateService(repository, null, null, null, null, com.pte.TestClocks.fixed());
        host = new CurrentUser(UUID.randomUUID(), TENANT, List.of("HOST_ADMIN"));
    }

    @Test
    void createFromDefault_copiesActiveDefaultIntoTenantOwnedDraft() {
        ExamDefinition base = defaultTemplate();
        when(repository.findWithItemsByStatusAndTenantIdIsNull(ExamDefinitionStatus.ACTIVE))
                .thenReturn(Optional.of(base));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ExamDefinitionResponse response = service.createFromDefault(new CreateHostExamTemplateRequest(" My exam "), host);

        assertThat(response.name()).isEqualTo("My exam");
        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.templatePolicy()).isEqualTo("CUSTOM");
        assertThat(response.tenantId()).isEqualTo(TENANT);
        assertThat(response.baseDefinitionPublicId()).isEqualTo(base.getPublicId());
        assertThat(response.code()).startsWith("HOST_");
        assertThat(response.items()).hasSize(base.getItems().size());
    }

    @Test
    void createFromDefault_withoutActiveDefault_isRejected() {
        when(repository.findWithItemsByStatusAndTenantIdIsNull(ExamDefinitionStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createFromDefault(new CreateHostExamTemplateRequest("x"), host))
                .isInstanceOf(NoActiveExamDefinitionException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void platformUser_cannotManageHostTemplates() {
        CurrentUser platform = new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"));

        assertThatThrownBy(() -> service.list(platform)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.createFromDefault(new CreateHostExamTemplateRequest("x"), platform))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void templateOfAnotherTenantOrPlatform_isNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findWithItemsByPublicIdAndTenantId(id, TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id, host)).isInstanceOf(ExamDefinitionNotFoundException.class);
        assertThatThrownBy(() -> service.activate(id, host)).isInstanceOf(ExamDefinitionNotFoundException.class);
        assertThatThrownBy(() -> service.deleteDraft(id, host)).isInstanceOf(ExamDefinitionNotFoundException.class);
    }

    @Test
    void replaceItems_keepingSubsetOfBase_isAccepted() {
        ExamDefinition base = defaultTemplate();
        ExamDefinition draft = hostTemplate(base, ExamDefinitionStatus.DRAFT, "HOST_A");
        stubOwned(draft);
        stubBase(base);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ExamDefinitionResponse response = service.replaceItems(draft.getPublicId(),
                new ReplaceExamDefinitionItemsRequest("Writing only",
                        List.of(itemRequest("CUSTOM_B", 0, "WRITING")), "CUSTOM"), host);

        assertThat(response.name()).isEqualTo("Writing only");
        assertThat(response.items()).hasSize(1);
    }

    @Test
    void replaceItems_withTaskTypeOutsideBase_isRejected() {
        ExamDefinition base = defaultTemplate();
        ExamDefinition draft = hostTemplate(base, ExamDefinitionStatus.DRAFT, "HOST_A");
        stubOwned(draft);
        stubBase(base);

        assertThatThrownBy(() -> service.replaceItems(draft.getPublicId(),
                new ReplaceExamDefinitionItemsRequest("Extra",
                        List.of(itemRequest("NOT_IN_DEFAULT", 0, "WRITING")), "CUSTOM"), host))
                .isInstanceOf(ExamDefinitionValidationException.class)
                .hasMessageContaining("NOT_IN_DEFAULT");
        verify(repository, never()).save(any());
    }

    @Test
    void replaceItems_onNonDraft_isRejected() {
        ExamDefinition active = hostTemplate(defaultTemplate(), ExamDefinitionStatus.ACTIVE, "HOST_A");
        stubOwned(active);

        assertThatThrownBy(() -> service.replaceItems(active.getPublicId(),
                new ReplaceExamDefinitionItemsRequest("x", List.of(), "CUSTOM"), host))
                .isInstanceOf(ExamDefinitionNotDraftException.class);
    }

    @Test
    void activate_retiresOnlySiblingsOfTheSameTenantAndCode() {
        ExamDefinition base = defaultTemplate();
        ExamDefinition previous = hostTemplate(base, ExamDefinitionStatus.ACTIVE, "HOST_A");
        previous.setVersion(1);
        ExamDefinition draft = hostTemplate(base, ExamDefinitionStatus.DRAFT, "HOST_A");
        draft.setVersion(2);
        stubOwned(draft);
        stubBase(base);
        when(repository.findAllWithItemsByTenantIdAndCodeAndStatus(TENANT, "HOST_A", ExamDefinitionStatus.ACTIVE))
                .thenReturn(List.of(previous));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ExamDefinitionResponse response = service.activate(draft.getPublicId(), host);

        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(previous.getStatus()).isEqualTo(ExamDefinitionStatus.RETIRED);
        // The platform default and other host templates are never looked up, so they cannot be retired.
        verify(repository, never()).findWithItemsByStatusAndTenantIdIsNull(any());
    }

    @Test
    void activate_withUnbalancedSkillWeights_changesNothing() {
        ExamDefinition base = defaultTemplate();
        ExamDefinition draft = hostTemplate(base, ExamDefinitionStatus.DRAFT, "HOST_A");
        draft.getItems().get(0).setSpeakingWeight(new BigDecimal("55"));
        stubOwned(draft);
        stubBase(base);

        assertThatThrownBy(() -> service.activate(draft.getPublicId(), host))
                .isInstanceOf(ExamDefinitionValidationException.class)
                .hasMessageContaining("SPEAKING");
        assertThat(draft.getStatus()).isEqualTo(ExamDefinitionStatus.DRAFT);
        verify(repository, never()).save(any());
    }

    @Test
    void retire_requiresAnActiveTemplate() {
        ExamDefinition draft = hostTemplate(defaultTemplate(), ExamDefinitionStatus.DRAFT, "HOST_A");
        stubOwned(draft);

        assertThatThrownBy(() -> service.retire(draft.getPublicId(), host))
                .isInstanceOf(ExamDefinitionValidationException.class);
    }

    @Test
    void list_returnsOnlyTheCallersTenant() {
        ExamDefinition own = hostTemplate(defaultTemplate(), ExamDefinitionStatus.DRAFT, "HOST_A");
        when(repository.findAllByTenantIdOrderByCodeAscVersionDesc(TENANT)).thenReturn(List.of(own));

        assertThat(service.list(host)).extracting(ExamDefinitionResponse::tenantId).containsExactly(TENANT);
        verify(repository, never()).findAllByTenantIdOrderByCodeAscVersionDesc(OTHER_TENANT);
    }

    private void stubOwned(ExamDefinition template) {
        when(repository.findWithItemsByPublicIdAndTenantId(template.getPublicId(), TENANT))
                .thenReturn(Optional.of(template));
    }

    private void stubBase(ExamDefinition base) {
        when(repository.findWithItemsByPublicIdAndTenantIdIsNull(base.getPublicId())).thenReturn(Optional.of(base));
    }

    /** The platform default: one scored task type per section, every skill totalling 100. */
    private ExamDefinition defaultTemplate() {
        ExamDefinition template = new ExamDefinition();
        template.setPublicId(UUID.randomUUID());
        template.setCode("DEFAULT");
        template.setVersion(1);
        template.setName("Default");
        template.setStatus(ExamDefinitionStatus.ACTIVE);
        template.setTemplatePolicy(TemplatePolicy.CUSTOM);
        template.addItem(item("CUSTOM_A", 0, "SPEAKING", "100", "0", "0", "0"));
        template.addItem(item("CUSTOM_B", 1, "WRITING", "0", "100", "0", "0"));
        template.addItem(item("CUSTOM_C", 2, "READING", "0", "0", "100", "0"));
        template.addItem(item("CUSTOM_D", 3, "LISTENING", "0", "0", "0", "100"));
        return template;
    }

    private ExamDefinition hostTemplate(ExamDefinition base, ExamDefinitionStatus status, String code) {
        ExamDefinition template = new ExamDefinition();
        template.setPublicId(UUID.randomUUID());
        template.setCode(code);
        template.setVersion(1);
        template.setName("Host template");
        template.setStatus(status);
        template.setTemplatePolicy(TemplatePolicy.CUSTOM);
        template.setTenantId(TENANT);
        template.setBaseDefinitionPublicId(base.getPublicId());
        new ArrayList<>(base.getItems()).forEach(source -> template.addItem(
                item(source.getTaskType(), source.getSequence(), source.getSection(),
                        source.getSpeakingWeight().toPlainString(), source.getWritingWeight().toPlainString(),
                        source.getReadingWeight().toPlainString(), source.getListeningWeight().toPlainString())));
        return template;
    }

    private ExamDefinitionItem item(String taskType, int sequence, String section, String speaking, String writing,
            String reading, String listening) {
        ExamDefinitionItem item = new ExamDefinitionItem();
        item.setTaskType(taskType);
        item.setSection(section);
        item.setSequence(sequence);
        item.setMinCount(1);
        item.setMaxCount(2);
        item.setPrepSeconds(0);
        item.setResponseSeconds(30);
        item.setScoringMethod(ScoringMethod.AI_SPEECH);
        item.setSpeakingWeight(new BigDecimal(speaking));
        item.setWritingWeight(new BigDecimal(writing));
        item.setReadingWeight(new BigDecimal(reading));
        item.setListeningWeight(new BigDecimal(listening));
        item.setOverallWeight(new BigDecimal("25"));
        item.pinRuntimeProfile(new TaskRuntimeProfileDescriptor(taskType, "PROFILE", 1, "BEHAVIOR", "RENDERER", 1,
                "AI_SPEECH", 1, List.of(), "ACTIVE"));
        return item;
    }

    private ExamDefinitionItemRequest itemRequest(String taskType, int sequence, String section) {
        return new ExamDefinitionItemRequest(taskType, section, sequence, 1, 2, 0, 30, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
