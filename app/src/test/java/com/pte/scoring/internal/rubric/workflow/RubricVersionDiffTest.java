package com.pte.scoring.internal.rubric.workflow;

import com.pte.scoring.domain.ScoringRubricVersion.RubricVersionStatus;
import com.pte.scoring.internal.rubric.repository.ScoringRubricVersionRepository;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RubricVersionDiffTest {
    @Test void diffNamesEveryChangedParameterAndHashesBothVersions() {
        var repo = mock(ScoringRubricVersionRepository.class);
        var active = version(RubricVersionStatus.ACTIVE, seedJson());
        var draft = version(RubricVersionStatus.DRAFT, editTask("DESCRIBE_IMAGE",
                task -> child(task, "traitWeights").put("CONTENT", 4)));
        when(repo.findByStatus(RubricVersionStatus.ACTIVE)).thenReturn(Optional.of(active));
        when(repo.findByPublicId(draft.getPublicId())).thenReturn(Optional.of(draft));
        var service = new RubricDiffService(repo);
        var diff = service.read(draft.getPublicId());
        assertThat(diff.changes()).singleElement().satisfies(change ->
                assertThat(change.path()).isEqualTo("taskTypes.DESCRIBE_IMAGE.traitWeights.CONTENT"));
        assertThat(diff.diffDigest()).hasSize(64).isEqualTo(service.read(draft.getPublicId()).diffDigest());
        assertThat(diff.expectedActiveVersionPublicId()).isEqualTo(active.getPublicId());
    }

    @Test void digestCoversLockChangesAndDiffIncludesGatesFormAndAzureVersion() {
        var repo = mock(ScoringRubricVersionRepository.class);
        var active = version(RubricVersionStatus.ACTIVE, seedJson());
        String parameters = editRoot(root -> {
            root.put("azureBandMappingVersion", 2);
            var essay = (tools.jackson.databind.node.ObjectNode) root.get("taskTypes").get("WRITE_ESSAY");
            child(essay, "gateEnabled").put("CONTENT", false);
            ((tools.jackson.databind.node.ObjectNode) essay.get("formBands").get(0)).put("minWords", 130);
        });
        var candidate = version(RubricVersionStatus.DRAFT, parameters);
        var service = new RubricDiffService(repo);
        var before = service.calculate(candidate, active);
        assertThat(before.changes()).extracting(change -> change.path()).contains("azureBandMappingVersion",
                "taskTypes.WRITE_ESSAY.gateEnabled.CONTENT", "taskTypes.WRITE_ESSAY.formBands[0].minWords");
        org.springframework.test.util.ReflectionTestUtils.setField(candidate, "lockVersion", 2L);
        String candidateChanged = service.calculate(candidate, active).diffDigest();
        assertThat(candidateChanged).isNotEqualTo(before.diffDigest());
        org.springframework.test.util.ReflectionTestUtils.setField(active, "lockVersion", 3L);
        assertThat(service.calculate(candidate, active).diffDigest()).isNotEqualTo(candidateChanged);
    }
}
