package com.pte.practice;

import com.pte.practice.internal.repository.PracticeResponseRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PracticeMediaReferenceServiceTest {

    private final PracticeResponseRepository repository = mock(PracticeResponseRepository.class);
    private final PracticeMediaReferenceService service = new PracticeMediaReferenceService(repository);

    @Test
    void aRecordingIsUsedOnlyWhileItsItemAnswerNamesIt() {
        UUID item = UUID.randomUUID();
        UUID current = UUID.randomUUID();
        UUID replaced = UUID.randomUUID();
        UUID otherItem = UUID.randomUUID();
        UUID ofDiscardedSession = UUID.randomUUID();
        // The discarded session's item is not returned by the query, as if it had no saved answer.
        when(repository.findLivePayloadsByPublicIds(any())).thenReturn(List.<Object[]>of(
                new Object[] {item, "{\"mediaPublicId\":\"" + current + "\",\"durationSeconds\":30}"}));

        var used = service.findReferencedResponseAudio(Map.of(current, item, replaced, item,
                ofDiscardedSession, otherItem));

        assertThat(used).containsExactly(current);
    }

    @Test
    void noRecordingsMeansNoQuery() {
        assertThat(service.findReferencedResponseAudio(Map.of())).isEmpty();
        verifyNoInteractions(repository);
    }
}
