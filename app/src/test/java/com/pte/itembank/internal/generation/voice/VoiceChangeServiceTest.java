package com.pte.itembank.internal.generation.voice;

import com.pte.itembank.domain.Question;
import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.itembank.domain.enums.QuestionPool;
import com.pte.itembank.domain.enums.QuestionStatus;
import com.pte.itembank.dto.response.QuestionResponse;
import com.pte.itembank.internal.exception.InvalidQuestionStatusTransitionException;
import com.pte.itembank.internal.exception.QuestionVersionConflictException;
import com.pte.itembank.internal.generation.constant.VoiceConstants;
import com.pte.itembank.internal.generation.exception.GenerationMediaException;
import com.pte.itembank.internal.generation.media.AudioDurationProbe;
import com.pte.itembank.internal.generation.media.EnabledVoices;
import com.pte.itembank.internal.generation.media.KokoroSpeechClient;
import com.pte.itembank.internal.generation.media.SpeechFitter;
import com.pte.itembank.internal.repository.QuestionRepository;
import com.pte.itembank.internal.service.ItembankAccessPolicy;
import com.pte.itembank.internal.service.QuestionMediaReferenceValidator;
import com.pte.media.MediaService;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VoiceChangeServiceTest {

    private static final String META = "{\"media\":{\"voice\":\"af_bella\",\"speed\":0.95,\"durationSec\":4.1,"
            + "\"audioPromptRef\":\"%s\"},\"model\":{\"generator\":\"Capstone\"},\"topic\":\"Daily Life\"}";

    private final QuestionRepository questions = mock(QuestionRepository.class);
    private final ItembankAccessPolicy policy = mock(ItembankAccessPolicy.class);
    private final AuditLogService audit = mock(AuditLogService.class);
    private final KokoroSpeechClient kokoro = mock(KokoroSpeechClient.class);
    private final AudioDurationProbe probe = mock(AudioDurationProbe.class);
    private final MediaService media = mock(MediaService.class);
    private final DraftWriteGuard guard = new DraftWriteGuard(policy, audit);
    private final QuestionVoiceWriter writer = new QuestionVoiceWriter(questions, guard,
            mock(QuestionMediaReferenceValidator.class), audit);
    private final VoiceChangeService service = new VoiceChangeService(questions,
            new EnabledVoices(propertiesDisabling(""), new MockEnvironment()), guard, new SpokenTextResolver(),
            new SpeechFitter(kokoro, probe), new VoiceChangeLimiter(), media, writer);
    private final CurrentUser caller = new CurrentUser(UUID.randomUUID(), UUID.randomUUID(), List.of("ACADEMIC_STAFF"));
    private final UUID oldAudio = UUID.randomUUID();
    private final UUID newAudio = UUID.randomUUID();
    private final Question question = draft();

    private static com.pte.itembank.internal.generation.config.QuestionGenerationProperties propertiesDisabling(
            String disabled) {
        var properties = new com.pte.itembank.internal.generation.config.QuestionGenerationProperties();
        properties.getKokoro().setVoicesDisabled(disabled);
        return properties;
    }

    private Question draft() {
        Question q = new Question();
        q.setPublicId(UUID.randomUUID());
        q.setPteTaskType(PteTaskType.REPEAT_SENTENCE);
        q.setStatus(QuestionStatus.DRAFT);
        q.setPool(QuestionPool.EXAM);
        q.setReferenceAnswerText("The city park offers a peaceful place for relaxation.");
        q.setAudioPromptRef(oldAudio);
        q.setGenerationMeta(String.format(META, oldAudio));
        return q;
    }

    private void ready() {
        when(questions.findWithOptionsByPublicId(question.getPublicId())).thenReturn(Optional.of(question));
        when(policy.canModifyDraft(any(), any())).thenReturn(true);
        when(kokoro.synthesize(anyString(), anyString(), anyDouble())).thenReturn(new byte[] {1, 2});
        when(probe.durationSeconds(any())).thenReturn(5.0);
        when(media.storeGenerated(any(), anyString(), anyString(), any(), any())).thenReturn(newAudio);
    }

    @Test
    void anotherVoiceGivesTheQuestionNewAudioAndKeepsTheOldFile() {
        ready();

        QuestionResponse response = service.changeVoice(question.getPublicId(), "bm_george", 0, caller);

        assertThat(question.getAudioPromptRef()).isEqualTo(newAudio).isNotEqualTo(oldAudio);
        assertThat(question.getGenerationMeta()).contains("\"voice\":\"bm_george\"", newAudio.toString(),
                "\"topic\":\"Daily Life\"", "\"generator\":\"Capstone\"");
        assertThat(response.audioPromptRef()).isEqualTo(newAudio);
        verify(media, never()).deleteGenerated(eq(oldAudio), any());
        verify(audit).record(eq(caller), anyString(), anyString(), eq("AUDIO_VOICE_CHANGED"), anyString());
    }

    @Test
    void anUnknownVoiceIsRefusedBeforeAnythingIsLoaded() {
        assertThatThrownBy(() -> service.changeVoice(question.getPublicId(), "xx_nobody", 0, caller))
                .isInstanceOfSatisfying(VoiceChangeException.class,
                        e -> assertThat(e.getMessage()).isEqualTo(VoiceConstants.GENERATION_VOICE_UNKNOWN));
        verify(questions, never()).findWithOptionsByPublicId(any());
    }

    @Test
    void theVoiceTheQuestionAlreadyHasIsRefusedWithoutCallingTheSpeechServer() {
        ready();

        assertThatThrownBy(() -> service.changeVoice(question.getPublicId(), "af_bella", 0, caller))
                .isInstanceOfSatisfying(VoiceChangeException.class,
                        e -> assertThat(e.getMessage()).isEqualTo(VoiceConstants.GENERATION_VOICE_UNCHANGED));
        verify(kokoro, never()).synthesize(anyString(), anyString(), anyDouble());
    }

    @Test
    void aDialogueAndAHandWrittenQuestionCannotBeChanged() {
        ready();
        question.setPteTaskType(PteTaskType.SUMMARIZE_GROUP_DISCUSSION);
        assertThatThrownBy(() -> service.changeVoice(question.getPublicId(), "bm_george", 0, caller))
                .isInstanceOfSatisfying(VoiceChangeException.class, e -> assertThat(e.getMessage())
                        .isEqualTo(VoiceConstants.GENERATION_VOICE_NOT_SUPPORTED_FOR_TASK));

        question.setPteTaskType(PteTaskType.REPEAT_SENTENCE);
        question.setGenerationMeta(null);
        assertThatThrownBy(() -> service.changeVoice(question.getPublicId(), "bm_george", 0, caller))
                .isInstanceOf(VoiceChangeException.class);
    }

    @Test
    void someoneWhoMayNotEditTheDraftIsDenied() {
        ready();
        when(policy.canModifyDraft(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.changeVoice(question.getPublicId(), "bm_george", 0, caller))
                .isInstanceOf(AccessDeniedException.class);
        verify(kokoro, never()).synthesize(anyString(), anyString(), anyDouble());
    }

    @Test
    void aQuestionThatIsNoLongerADraftIsRefused() {
        ready();
        question.setStatus(QuestionStatus.APPROVED);

        assertThatThrownBy(() -> service.changeVoice(question.getPublicId(), "bm_george", 0, caller))
                .isInstanceOf(InvalidQuestionStatusTransitionException.class);
    }

    @Test
    void aStaleVersionIsRefusedBeforeSpeaking() {
        ready();

        assertThatThrownBy(() -> service.changeVoice(question.getPublicId(), "bm_george", 7, caller))
                .isInstanceOf(QuestionVersionConflictException.class);
        verify(kokoro, never()).synthesize(anyString(), anyString(), anyDouble());
    }

    @Test
    void aQuestionThatMovedOnWhileSpeakingKeepsItsAudioAndTheNewFileIsRemoved() {
        ready();
        when(kokoro.synthesize(anyString(), anyString(), anyDouble())).thenAnswer(call -> {
            question.setStatus(QuestionStatus.APPROVED);
            return new byte[] {1, 2};
        });

        assertThatThrownBy(() -> service.changeVoice(question.getPublicId(), "bm_george", 0, caller))
                .isInstanceOf(InvalidQuestionStatusTransitionException.class);

        assertThat(question.getAudioPromptRef()).isEqualTo(oldAudio);
        verify(media).deleteGenerated(newAudio, caller);
        verify(media, never()).deleteGenerated(eq(oldAudio), any());
    }

    @Test
    void audioOutsideTheLengthRuleIsNotStored() {
        ready();
        when(probe.durationSeconds(any())).thenReturn(30.0);

        assertThatThrownBy(() -> service.changeVoice(question.getPublicId(), "bm_george", 0, caller))
                .isInstanceOf(GenerationMediaException.class);
        verify(media, never()).storeGenerated(any(), anyString(), anyString(), any(), any());
    }

    @Test
    void aSpeechServerFailureIsReportedWithoutItsDetails() {
        ready();
        when(kokoro.synthesize(anyString(), anyString(), anyDouble()))
                .thenThrow(new IllegalStateException("https://secret-host/v1 said no"));

        assertThatThrownBy(() -> service.changeVoice(question.getPublicId(), "bm_george", 0, caller))
                .isInstanceOfSatisfying(GenerationMediaException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(e.getMessage()).doesNotContain("secret-host");
                });
    }
}
