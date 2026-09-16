package com.pte.scoretemplate.internal.service;

import com.pte.scoretemplate.domain.ScoreTemplate;
import com.pte.scoretemplate.domain.ScoreTemplateItem;
import com.pte.scoretemplate.domain.enums.ScoreTemplateStatus;
import com.pte.scoretemplate.domain.enums.ScoringMethod;
import com.pte.scoretemplate.domain.enums.TimingMode;
import com.pte.scoretemplate.dto.request.ReplaceScoreTemplateItemsRequest;
import com.pte.scoretemplate.dto.request.ScoreTemplateItemRequest;
import com.pte.scoretemplate.dto.response.ScoreTemplateResponse;
import com.pte.scoretemplate.internal.exception.ScoreTemplateConcurrentModificationException;
import com.pte.scoretemplate.internal.exception.ScoreTemplateNotDraftException;
import com.pte.scoretemplate.internal.exception.ScoreTemplateValidationException;
import com.pte.scoretemplate.internal.repository.ScoreTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Mirrors SnapshotPublishServiceTest's style: Mockito, no Spring context. */
@ExtendWith(MockitoExtension.class)
class ScoreTemplateAdminServiceTest {

    @Mock
    private ScoreTemplateRepository repository;

    private ScoreTemplateAdminService service;

    @BeforeEach
    void setUp() {
        service = new ScoreTemplateAdminService(repository);
    }

    /** 22 items covering every required task type + every skill weight, valid per FR-05. */
    private ScoreTemplate fullyValidTemplate(UUID publicId, String code, int version, ScoreTemplateStatus status) {
        ScoreTemplate template = new ScoreTemplate();
        template.setPublicId(publicId);
        template.setCode(code);
        template.setVersion(version);
        template.setName(code + " v" + version);
        template.setStatus(status);
        List<String> taskTypes = List.of(
                "READ_ALOUD", "REPEAT_SENTENCE", "DESCRIBE_IMAGE", "RE_TELL_LECTURE", "ANSWER_SHORT_QUESTION",
                "RESPOND_TO_A_SITUATION", "SUMMARIZE_GROUP_DISCUSSION",
                "SUMMARIZE_WRITTEN_TEXT", "WRITE_ESSAY",
                "MC_READING_SINGLE", "MC_READING_MULTIPLE", "RE_ORDER_PARAGRAPHS", "FILL_BLANKS_READING",
                "FILL_BLANKS_READING_WRITING",
                "SUMMARIZE_SPOKEN_TEXT", "MC_LISTENING_SINGLE", "MC_LISTENING_MULTIPLE", "FILL_BLANKS_LISTENING",
                "HIGHLIGHT_CORRECT_SUMMARY", "SELECT_MISSING_WORD", "HIGHLIGHT_INCORRECT_WORDS", "WRITE_FROM_DICTATION");
        int seq = 0;
        for (String taskType : taskTypes) {
            ScoreTemplateItem item = new ScoreTemplateItem();
            item.setTaskType(taskType);
            item.setSection("SPEAKING");
            item.setSequence(seq++);
            item.setMinCount(1);
            item.setMaxCount(2);
            item.setPrepSeconds(0);
            item.setResponseSeconds(30);
            item.setTimingMode(TimingMode.FIXED);
            item.setScoringMethod(ScoringMethod.AI_SPEECH);
            item.setOverallWeight(BigDecimal.ONE);
            item.setSpeakingWeight(BigDecimal.ONE);
            item.setWritingWeight(BigDecimal.ONE);
            item.setReadingWeight(BigDecimal.ONE);
            item.setListeningWeight(BigDecimal.ONE);
            template.addItem(item);
        }
        return template;
    }

    @Test
    void activate_flipsOldActiveToRetired_inSameCall() {
        UUID oldActiveId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        ScoreTemplate oldActive = fullyValidTemplate(oldActiveId, "APEUNI_V5", 1, ScoreTemplateStatus.ACTIVE);
        ScoreTemplate target = fullyValidTemplate(targetId, "APEUNI_V5", 2, ScoreTemplateStatus.DRAFT);
        when(repository.findWithItemsByPublicId(targetId)).thenReturn(Optional.of(target));
        when(repository.findAllByCodeForUpdate("APEUNI_V5")).thenReturn(List.of(oldActive, target));
        when(repository.findWithItemsByStatus(ScoreTemplateStatus.ACTIVE)).thenReturn(Optional.of(oldActive));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ScoreTemplateResponse response = service.activate(targetId);

        assertThat(oldActive.getStatus()).isEqualTo(ScoreTemplateStatus.RETIRED);
        assertThat(target.getStatus()).isEqualTo(ScoreTemplateStatus.ACTIVE);
        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    void activate_invalidTemplate_changesNoState() {
        UUID targetId = UUID.randomUUID();
        ScoreTemplate invalid = new ScoreTemplate(); // no items -> fails FR-05
        invalid.setPublicId(targetId);
        invalid.setCode("APEUNI_V5");
        invalid.setVersion(2);
        invalid.setName("broken");
        invalid.setStatus(ScoreTemplateStatus.DRAFT);
        when(repository.findWithItemsByPublicId(targetId)).thenReturn(Optional.of(invalid));

        assertThatThrownBy(() -> service.activate(targetId)).isInstanceOf(ScoreTemplateValidationException.class);

        assertThat(invalid.getStatus()).isEqualTo(ScoreTemplateStatus.DRAFT);
        verify(repository, never()).save(any());
    }

    @Test
    void activate_repositoryRaceViolation_translatesTo409() {
        UUID targetId = UUID.randomUUID();
        ScoreTemplate target = fullyValidTemplate(targetId, "APEUNI_V5", 2, ScoreTemplateStatus.DRAFT);
        when(repository.findWithItemsByPublicId(targetId)).thenReturn(Optional.of(target));
        when(repository.findAllByCodeForUpdate("APEUNI_V5")).thenReturn(List.of(target));
        when(repository.findWithItemsByStatus(ScoreTemplateStatus.ACTIVE)).thenReturn(Optional.empty());
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> service.activate(targetId))
                .isInstanceOf(ScoreTemplateConcurrentModificationException.class);
    }

    @Test
    void replaceItems_onNonDraftTemplate_throwsNotDraft() {
        UUID publicId = UUID.randomUUID();
        ScoreTemplate active = fullyValidTemplate(publicId, "APEUNI_V5", 1, ScoreTemplateStatus.ACTIVE);
        when(repository.findWithItemsByPublicId(publicId)).thenReturn(Optional.of(active));

        var request = new ReplaceScoreTemplateItemsRequest("new name", List.of(sampleItemRequest()));

        assertThatThrownBy(() -> service.replaceItems(publicId, request)).isInstanceOf(ScoreTemplateNotDraftException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void cloneToDraft_copiesAllItemsAndIncrementsVersion() {
        UUID sourceId = UUID.randomUUID();
        ScoreTemplate source = fullyValidTemplate(sourceId, "APEUNI_V5", 3, ScoreTemplateStatus.ACTIVE);
        when(repository.findWithItemsByPublicId(sourceId)).thenReturn(Optional.of(source));
        when(repository.findAllByCodeForUpdate("APEUNI_V5")).thenReturn(List.of(source));
        when(repository.findMaxVersionByCode("APEUNI_V5")).thenReturn(3);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ScoreTemplateResponse response = service.cloneToDraft(sourceId);

        assertThat(response.version()).isEqualTo(4);
        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.items()).hasSize(source.getItems().size());
    }

    @Test
    void cloneToDraft_repositoryRaceViolation_translatesTo409() {
        UUID sourceId = UUID.randomUUID();
        ScoreTemplate source = fullyValidTemplate(sourceId, "APEUNI_V5", 3, ScoreTemplateStatus.ACTIVE);
        when(repository.findWithItemsByPublicId(sourceId)).thenReturn(Optional.of(source));
        when(repository.findAllByCodeForUpdate("APEUNI_V5")).thenReturn(List.of(source));
        when(repository.findMaxVersionByCode("APEUNI_V5")).thenReturn(3);
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> service.cloneToDraft(sourceId))
                .isInstanceOf(ScoreTemplateConcurrentModificationException.class);
    }

    private ScoreTemplateItemRequest sampleItemRequest() {
        return new ScoreTemplateItemRequest("READ_ALOUD", "SPEAKING", 0, 6, 7, 35, 40, "FIXED", "AI_SPEECH",
                BigDecimal.valueOf(4), BigDecimal.valueOf(9), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
