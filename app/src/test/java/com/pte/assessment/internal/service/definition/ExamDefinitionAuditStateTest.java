package com.pte.assessment.internal.service.definition;

import com.pte.assessment.domain.ExamDefinition;
import com.pte.assessment.domain.ExamDefinitionItem;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ExamDefinitionAuditStateTest {

    @Test
    void itemsAreOrderedBySequence() {
        ExamDefinition template = new ExamDefinition();
        template.setName("PTE 22 task type");
        template.addItem(item(2, "WRITE_ESSAY"));
        template.addItem(item(1, "READ_ALOUD"));

        ExamDefinitionAuditState state = ExamDefinitionAuditState.of(template);

        assertThat(state.name()).isEqualTo("PTE 22 task type");
        assertThat(state.items()).extracting(ExamDefinitionAuditState.ItemState::taskTypeKey)
                .containsExactly("READ_ALOUD", "WRITE_ESSAY");
    }

    @Test
    void runtimeColumnsResolvedFromTheTaskTypeAreNotPartOfTheSnapshot() {
        assertThat(Arrays.stream(ExamDefinitionAuditState.ItemState.class.getRecordComponents())
                .map(RecordComponent::getName))
                .noneMatch(name -> name.toLowerCase().startsWith("runtime"));
    }

    private static ExamDefinitionItem item(int sequence, String taskTypeKey) {
        ExamDefinitionItem item = new ExamDefinitionItem();
        item.setSequence(sequence);
        item.setTaskTypeKey(taskTypeKey);
        item.setMinCount(1);
        item.setMaxCount(2);
        return item;
    }
}
