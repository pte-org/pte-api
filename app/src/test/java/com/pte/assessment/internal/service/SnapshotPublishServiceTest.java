package com.pte.assessment.internal.service;

import com.pte.assessment.domain.BlueprintItem;
import com.pte.assessment.domain.ExamBlueprint;
import com.pte.assessment.domain.ExamSnapshot;
import com.pte.assessment.domain.enums.BlueprintStatus;
import com.pte.assessment.dto.response.SnapshotResponse;
import com.pte.assessment.internal.exception.BlueprintNotFoundException;
import com.pte.assessment.internal.exception.EmptyBlueprintException;
import com.pte.assessment.internal.repository.ExamBlueprintRepository;
import com.pte.assessment.internal.repository.ExamSnapshotRepository;
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
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Covers Phase 05/A's snapshot-freeze boundary: content comes only from
 * {@link ItembankService#freeze}, never {@code itembank}'s repository, and
 * the frozen options JSON shape stays what {@code scoring} (a later phase)
 * will parse — ported/adapted from services/authoring's own
 * {@code SnapshotPublishServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class SnapshotPublishServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();

    @Mock
    private ExamBlueprintRepository blueprintRepository;
    @Mock
    private ExamSnapshotRepository snapshotRepository;
    @Mock
    private ItembankService itembankService;

    private SnapshotPublishService service;
    private CurrentUser caller;

    @BeforeEach
    void setUp() {
        service = new SnapshotPublishService(blueprintRepository, snapshotRepository, itembankService,
                new AssessmentAccessPolicy(), JsonMapper.builder().build());
        caller = new CurrentUser(UUID.randomUUID(), TENANT_ID, List.of("HOST_AUTHOR"));
    }

    private ExamBlueprint blueprintWithOneItem(UUID questionPublicId) {
        ExamBlueprint blueprint = new ExamBlueprint();
        blueprint.setName("Mock Test A");
        blueprint.setTenantId(TENANT_ID);
        BlueprintItem item = new BlueprintItem();
        item.setQuestionPublicId(questionPublicId);
        item.setSection(PteSection.READING);
        item.setOrderIndex(0);
        blueprint.addItem(item);
        return blueprint;
    }

    private QuestionFreezeView frozenQuestion(UUID questionPublicId) {
        return new QuestionFreezeView(
                questionPublicId, PteTaskType.MC_READING_SINGLE, "title", "prompt",
                null, null, "ref", "correct", null, null,
                List.of(new QuestionFreezeView.Option("A", true, 0, null, null)));
    }

    @Test
    void publish_emptyBlueprint_throws() {
        UUID blueprintId = UUID.randomUUID();
        ExamBlueprint blueprint = new ExamBlueprint();
        blueprint.setTenantId(TENANT_ID);
        when(blueprintRepository.findWithItemsByPublicId(blueprintId)).thenReturn(Optional.of(blueprint));

        assertThatThrownBy(() -> service.publish(blueprintId, caller)).isInstanceOf(EmptyBlueprintException.class);
    }

    @Test
    void publish_unknownBlueprint_throwsNotFound() {
        UUID blueprintId = UUID.randomUUID();
        when(blueprintRepository.findWithItemsByPublicId(blueprintId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.publish(blueprintId, caller)).isInstanceOf(BlueprintNotFoundException.class);
    }

    @Test
    void publish_freezesEachItemThroughItembankService_neverRepository() {
        UUID blueprintId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        ExamBlueprint blueprint = blueprintWithOneItem(questionId);
        when(blueprintRepository.findWithItemsByPublicId(blueprintId)).thenReturn(Optional.of(blueprint));
        when(itembankService.freeze(questionId)).thenReturn(frozenQuestion(questionId));
        when(snapshotRepository.countBySourceBlueprintPublicId(blueprintId)).thenReturn(0L);
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SnapshotResponse response = service.publish(blueprintId, caller);

        assertThat(response.version()).isEqualTo(1);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).title()).isEqualTo("title");
        assertThat(blueprint.getStatus()).isEqualTo(BlueprintStatus.PUBLISHED);
    }

    @Test
    void publish_secondPublish_incrementsVersion() {
        UUID blueprintId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        ExamBlueprint blueprint = blueprintWithOneItem(questionId);
        when(blueprintRepository.findWithItemsByPublicId(blueprintId)).thenReturn(Optional.of(blueprint));
        when(itembankService.freeze(questionId)).thenReturn(frozenQuestion(questionId));
        when(snapshotRepository.countBySourceBlueprintPublicId(blueprintId)).thenReturn(1L);
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SnapshotResponse response = service.publish(blueprintId, caller);

        assertThat(response.version()).isEqualTo(2);
    }

    @Test
    void publish_optionsJsonContainsCorrectFlagAndGapIndex_forScoringToParse() {
        UUID blueprintId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        ExamBlueprint blueprint = blueprintWithOneItem(questionId);
        when(blueprintRepository.findWithItemsByPublicId(blueprintId)).thenReturn(Optional.of(blueprint));
        when(itembankService.freeze(questionId)).thenReturn(new QuestionFreezeView(
                questionId, PteTaskType.FILL_BLANKS_READING, "title", "prompt", null, null, null, null, null, null,
                List.of(new QuestionFreezeView.Option("word", true, 0, null, 3))));
        when(snapshotRepository.countBySourceBlueprintPublicId(blueprintId)).thenReturn(0L);
        var captor = org.mockito.ArgumentCaptor.forClass(ExamSnapshot.class);
        when(snapshotRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        service.publish(blueprintId, caller);

        String optionsJson = captor.getValue().getItems().get(0).getOptionsJson();
        assertThat(optionsJson).contains("\"correct\":true").contains("\"correctGapIndex\":3");
    }

    @Test
    void getSummary_unknownSnapshot_throwsNotFound() {
        UUID publicId = UUID.randomUUID();
        when(snapshotRepository.findWithItemsByPublicId(publicId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSummary(publicId)).isInstanceOf(BlueprintNotFoundException.class);
    }

    @Test
    void getContent_returnsFullFidelityIncludingAnswerKey() {
        UUID publicId = UUID.randomUUID();
        ExamSnapshot snapshot = new ExamSnapshot();
        snapshot.setName("Mock Test A");
        snapshot.setVersion(1);
        snapshot.setSourceBlueprintPublicId(UUID.randomUUID());
        when(snapshotRepository.findWithItemsByPublicId(publicId)).thenReturn(Optional.of(snapshot));

        var content = service.getContent(publicId);

        assertThat(content.name()).isEqualTo("Mock Test A");
    }
}
