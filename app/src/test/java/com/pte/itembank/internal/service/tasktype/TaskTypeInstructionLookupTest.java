package com.pte.itembank.internal.service.tasktype;

import com.pte.itembank.domain.QuestionType;
import com.pte.itembank.internal.repository.QuestionTypeRepository;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskTypeInstructionLookupTest {

    private final QuestionTypeRepository repository = mock(QuestionTypeRepository.class);
    private final TaskTypeQueryService service = new TaskTypeQueryService(repository, null, null, null);

    @Test
    void oneQueryReturnsCanonicalKeysWithNonBlankInstructionsOnly() {
        when(repository.findAllByTaskTypeKeyIn(
                Set.of("READ_ALOUD", "FILL_IN_THE_BLANKS_TYPE_IN", "CUSTOM_LISTEN")))
                .thenReturn(List.of(
                        definition("READ_ALOUD", "Read the text aloud."),
                        definition("FILL_IN_THE_BLANKS_TYPE_IN", "  "),
                        definition("CUSTOM_LISTEN", null)));

        Map<String, String> instructions = service.findInstructionsByTaskTypeKeys(
                Arrays.asList("read_aloud", "READ_ALOUD", "FILL_BLANKS_LISTENING", "CUSTOM_LISTEN", null));

        assertThat(instructions).containsExactly(Map.entry("READ_ALOUD", "Read the text aloud."));
        verify(repository).findAllByTaskTypeKeyIn(any());
    }

    @Test
    void noKeysSkipsTheQuery() {
        assertThat(service.findInstructionsByTaskTypeKeys(List.of())).isEmpty();
        verify(repository, never()).findAllByTaskTypeKeyIn(any());
    }

    private static QuestionType definition(String taskTypeKey, String instruction) {
        QuestionType definition = new QuestionType();
        definition.setTaskTypeKey(taskTypeKey);
        definition.setInstruction(instruction);
        return definition;
    }
}
