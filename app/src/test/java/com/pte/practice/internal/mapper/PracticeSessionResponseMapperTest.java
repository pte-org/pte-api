package com.pte.practice.internal.mapper;

import com.pte.media.MediaService;
import com.pte.media.dto.response.PresignedDownloadResponse;
import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.practice.internal.domain.PracticeSession;
import com.pte.practice.internal.domain.PracticeSessionItem;
import com.pte.practice.internal.domain.enums.PracticeSessionItemStatus;
import com.pte.practice.internal.domain.enums.PracticeSessionStatus;
import com.pte.practice.internal.dto.response.PracticeCatalogResponse;
import com.pte.practice.internal.dto.response.PracticeSessionResponse;
import com.pte.practice.internal.service.PracticeCatalogService;
import com.pte.practice.internal.service.PracticeSessionItemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PracticeSessionResponseMapperTest {

    private static final String SECRET_REFERENCE = "SECRET-REFERENCE-ANSWER";
    private static final String SECRET_CORRECT = "SECRET-CORRECT-ANSWER";
    private static final String SECRET_OPTION_KEY = "SECRET-OPTION-KEY";

    @Mock
    private PracticeCatalogService catalogService;
    @Mock
    private PracticeSessionItemService itemService;
    @Mock
    private MediaService mediaService;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private PracticeSessionResponseMapper mapper;
    private PracticeSession session;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        mapper = new PracticeSessionResponseMapper(catalogService, itemService, jsonMapper, mediaService);
        tenantId = UUID.randomUUID();
        session = new PracticeSession();
        session.setPublicId(UUID.randomUUID());
        session.setTenantId(tenantId);
        session.setStatus(PracticeSessionStatus.IN_PROGRESS);
        session.setTimeLimitSeconds(PracticeConstants.PRACTICE_NO_SESSION_TIME_LIMIT_SECONDS);
        when(catalogService.getCatalog()).thenReturn(new PracticeCatalogResponse(
                PracticeConstants.PRACTICE_PRODUCT_CODE, "t", "v", 3600, List.of()));
    }

    @Test
    void promptAudioAndImageAreExposedAsPresignedUrlsWithTheStandaloneTtl() {
        UUID audio = UUID.randomUUID();
        UUID image = UUID.randomUUID();
        PracticeSessionItem item = pendingItem();
        item.setAudioPromptRef(audio);
        item.setImagePromptRef(image);
        when(itemService.itemsFor(session)).thenReturn(List.of(item));
        when(mediaService.presignGetAll(any(), anyLong(), any())).thenReturn(Map.of(
                audio, new PresignedDownloadResponse("https://cdn.test/audio.wav", 7200, null),
                image, new PresignedDownloadResponse("https://cdn.test/image.png", 7200, null)));

        PracticeSessionResponse response = mapper.toResponse(session);

        assertThat(response.currentTask().content().audioUrl()).isEqualTo("https://cdn.test/audio.wav");
        assertThat(response.currentTask().content().imageUrl()).isEqualTo("https://cdn.test/image.png");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> ids = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<Long> ttl = ArgumentCaptor.forClass(Long.class);
        verify(mediaService).presignGetAll(ids.capture(), ttl.capture(), org.mockito.ArgumentMatchers.eq(tenantId));
        assertThat(ids.getValue()).containsExactlyInAnyOrder(audio, image);
        assertThat(ttl.getValue()).isEqualTo(PracticeConstants.PRACTICE_PROMPT_MEDIA_TTL_SECONDS);
    }

    @Test
    void referenceAnswersCorrectAnswersAndAnswerKeyOptionsNeverReachTheStudentResponse() throws Exception {
        PracticeSessionItem item = pendingItem();
        item.setReferenceAnswerText(SECRET_REFERENCE);
        item.setCorrectAnswerText(SECRET_CORRECT);
        item.setObjectiveOptionsJson("[{\"publicId\":\"" + UUID.randomUUID() + "\",\"text\":\"" + SECRET_OPTION_KEY
                + "\",\"correct\":true}]");
        item.setOptionsJson("[{\"publicId\":\"" + UUID.randomUUID() + "\",\"text\":\"Visible option\","
                + "\"orderIndex\":0,\"blankIndex\":null}]");
        when(itemService.itemsFor(session)).thenReturn(List.of(item));

        String json = jsonMapper.writeValueAsString(mapper.toResponse(session));

        assertThat(json).contains("Visible option")
                .doesNotContain(SECRET_REFERENCE)
                .doesNotContain(SECRET_CORRECT)
                .doesNotContain(SECRET_OPTION_KEY)
                .doesNotContain("referenceAnswer")
                .doesNotContain("correctAnswer")
                .doesNotContain("\"correct\"");
    }

    @Test
    void anItemWithoutPromptMediaNeverCallsTheMediaFacade() {
        PracticeSessionItem item = pendingItem();
        when(itemService.itemsFor(session)).thenReturn(List.of(item));

        PracticeSessionResponse response = mapper.toResponse(session);

        assertThat(response.currentTask().content().audioUrl()).isNull();
        assertThat(response.currentTask().content().imageUrl()).isNull();
        verify(mediaService, never()).presignGetAll(any(), anyLong(), any());
    }

    @Test
    void aPresignFailureLeavesTheUrlEmptyInsteadOfFailingTheSessionRead() {
        PracticeSessionItem item = pendingItem();
        item.setAudioPromptRef(UUID.randomUUID());
        when(itemService.itemsFor(session)).thenReturn(List.of(item));
        when(mediaService.presignGetAll(any(), anyLong(), any())).thenThrow(new IllegalStateException("cdn down"));

        PracticeSessionResponse response = mapper.toResponse(session);

        assertThat(response.currentTask().content().audioPromptRef()).isNotNull();
        assertThat(response.currentTask().content().audioUrl()).isNull();
    }

    private PracticeSessionItem pendingItem() {
        PracticeSessionItem item = new PracticeSessionItem();
        item.setPublicId(UUID.randomUUID());
        item.setOrderIndex(0);
        item.setTaskCode("READ_ALOUD");
        item.setDisplayName("Read Aloud");
        item.setSection("SPEAKING");
        item.setRendererKey("READ_ALOUD_V1");
        item.setStatus(PracticeSessionItemStatus.PENDING);
        item.setPromptText("Read this text aloud.");
        return item;
    }
}
