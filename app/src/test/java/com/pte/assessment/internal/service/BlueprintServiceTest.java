package com.pte.assessment.internal.service;

import com.pte.assessment.domain.ExamBlueprint;
import com.pte.assessment.internal.dto.response.BlueprintResponse;
import com.pte.assessment.internal.exception.BlueprintNotFoundException;
import com.pte.assessment.internal.repository.ExamBlueprintRepository;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** Covers platform audit reads for resolver-generated blueprint artifacts. */
@ExtendWith(MockitoExtension.class)
class BlueprintServiceTest {

    @Mock
    private ExamBlueprintRepository blueprintRepository;

    private BlueprintService service;
    private CurrentUser platformCaller;

    @BeforeEach
    void setUp() {
        service = new BlueprintService(blueprintRepository, new AssessmentAccessPolicy());
        platformCaller = new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"));
    }

    @Test
    void get_generatedBlueprint_returnsAuditView() {
        UUID publicId = UUID.randomUUID();
        ExamBlueprint blueprint = new ExamBlueprint();
        blueprint.setPublicId(publicId);
        blueprint.setName("Generated test");
        blueprint.setTenantId(null);
        when(blueprintRepository.findWithItemsByPublicId(publicId)).thenReturn(Optional.of(blueprint));

        BlueprintResponse response = service.get(publicId, platformCaller);

        assertThat(response.publicId()).isEqualTo(publicId);
        assertThat(response.items()).isEmpty();
    }

    @Test
    void get_unknownBlueprint_throwsNotFound() {
        UUID publicId = UUID.randomUUID();
        when(blueprintRepository.findWithItemsByPublicId(publicId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(publicId, platformCaller)).isInstanceOf(BlueprintNotFoundException.class);
    }

    @Test
    void list_platformUserReadsGeneratedBlueprints() {
        ExamBlueprint blueprint = new ExamBlueprint();
        blueprint.setName("Generated test");
        blueprint.setTenantId(null);
        when(blueprintRepository.findByTenantIdIsNull()).thenReturn(List.of(blueprint));

        assertThat(service.list(platformCaller)).hasSize(1);
    }
}
