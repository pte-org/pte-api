package com.pte.assessment.internal.repository;

import com.pte.assessment.domain.ExamDefinition;
import com.pte.assessment.domain.ExamDefinitionItem;
import com.pte.assessment.domain.ExamVersion;
import com.pte.assessment.domain.ExamVersionItem;
import com.pte.assessment.domain.enums.ExamDefinitionStatus;
import com.pte.assessment.domain.enums.ScoringMethod;
import com.pte.itembank.domain.enums.PteSection;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Frozen exam items and definition rows are child tables of their aggregate, not entities. */
@DataJpaTest
class ExamVersionRepositoryTest {

    @Autowired
    private ExamVersionRepository versions;

    @Autowired
    private ExamDefinitionRepository definitions;

    @Autowired
    private EntityManager entityManager;

    private ExamVersionItem item(int orderIndex, String title) {
        ExamVersionItem item = new ExamVersionItem();
        item.setSourceQuestionPublicId(UUID.randomUUID());
        item.setTaskTypeKey("READ_ALOUD");
        item.setSection(PteSection.SPEAKING);
        item.setOrderIndex(orderIndex);
        item.setTitle(title);
        return item;
    }

    private ExamVersion version(UUID tenantId, ExamVersionItem... items) {
        ExamVersion version = new ExamVersion();
        version.setName("Frozen exam");
        version.setVersion(1);
        version.setSourceBlueprintPublicId(UUID.randomUUID());
        version.setExamDefinitionPublicId(UUID.randomUUID());
        version.setExamDefinitionVersion(1);
        version.setTenantId(tenantId);
        for (ExamVersionItem item : items) {
            version.addItem(item);
        }
        return versions.saveAndFlush(version);
    }

    @Test
    void itemsAreReloadedInOrderWithTheirStableIds() {
        ExamVersionItem second = item(1, "second");
        ExamVersionItem first = item(0, "first");
        ExamVersion saved = version(UUID.randomUUID(), second, first);
        UUID firstId = first.getPublicId();
        entityManager.clear();

        ExamVersion reloaded = versions.findWithItemsByPublicId(saved.getPublicId()).orElseThrow();

        assertThat(reloaded.getItems()).extracting(ExamVersionItem::getTitle).containsExactly("first", "second");
        assertThat(reloaded.getItems().get(0).getPublicId()).isEqualTo(firstId);
    }

    @Test
    void examinerPromptLookupFindsOnlyItemsOfTheTenantsOwnVersions() {
        UUID tenant = UUID.randomUUID();
        ExamVersionItem mine = item(0, "mine");
        ExamVersionItem foreign = item(0, "foreign");
        version(tenant, mine);
        version(UUID.randomUUID(), foreign);
        entityManager.clear();

        List<ExamVersionItem> found = versions.findItemsForTenant(
                List.of(mine.getPublicId(), foreign.getPublicId()), tenant);

        assertThat(found).extracting(ExamVersionItem::getTitle).containsExactly("mine");
    }

    @Test
    void definitionItemsAreAChildTableReloadedInSequenceOrder() {
        ExamDefinition definition = new ExamDefinition();
        definition.setCode("TEST_DEFINITION");
        definition.setVersion(1);
        definition.setName("Definition");
        definition.setStatus(ExamDefinitionStatus.DRAFT);
        definition.addItem(definitionItem(2, "WRITE_ESSAY"));
        definition.addItem(definitionItem(1, "READ_ALOUD"));
        definitions.saveAndFlush(definition);
        entityManager.clear();

        ExamDefinition reloaded = definitions.findWithItemsByPublicId(definition.getPublicId()).orElseThrow();

        assertThat(reloaded.getItems()).extracting(ExamDefinitionItem::getTaskTypeKey)
                .containsExactly("READ_ALOUD", "WRITE_ESSAY");
    }

    private ExamDefinitionItem definitionItem(int sequence, String taskTypeKey) {
        ExamDefinitionItem item = new ExamDefinitionItem();
        item.setTaskType(taskTypeKey);
        item.setTaskTypeKey(taskTypeKey);
        item.setSection("SPEAKING");
        item.setSequence(sequence);
        item.setMinCount(1);
        item.setMaxCount(1);
        item.setPrepSeconds(10);
        item.setResponseSeconds(40);
        item.setScoringMethod(ScoringMethod.AI_SPEECH);
        item.setOverallWeight(BigDecimal.ONE);
        return item;
    }
}
