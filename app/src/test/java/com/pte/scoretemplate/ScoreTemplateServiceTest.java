package com.pte.scoretemplate;

import com.pte.scoretemplate.domain.ScoreTemplate;
import com.pte.scoretemplate.domain.enums.ScoreTemplateStatus;
import com.pte.scoretemplate.dto.response.ScoreTemplateResponse;
import com.pte.scoretemplate.internal.exception.NoActiveScoreTemplateException;
import com.pte.scoretemplate.internal.exception.ScoreTemplateNotFoundException;
import com.pte.scoretemplate.internal.repository.ScoreTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * The only door assessment/attempt/scoring/reporting use to reach
 * scoretemplate (see plan.md). Read-only, no PLATFORM_ADMIN gate — those
 * modules are trusted application callers, not end users.
 */
@ExtendWith(MockitoExtension.class)
class ScoreTemplateServiceTest {

    @Mock
    private ScoreTemplateRepository repository;

    private ScoreTemplateService service;

    @BeforeEach
    void setUp() {
        service = new ScoreTemplateService(repository);
    }

    @Test
    void getActive_noActiveTemplate_throws() {
        when(repository.findWithItemsByStatus(ScoreTemplateStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getActive()).isInstanceOf(NoActiveScoreTemplateException.class);
    }

    @Test
    void getActive_returnsActiveTemplate() {
        ScoreTemplate active = new ScoreTemplate();
        active.setPublicId(UUID.randomUUID());
        active.setCode("APEUNI_V5");
        active.setVersion(1);
        active.setName("APEUni V5");
        active.setStatus(ScoreTemplateStatus.ACTIVE);
        when(repository.findWithItemsByStatus(ScoreTemplateStatus.ACTIVE)).thenReturn(Optional.of(active));

        ScoreTemplateResponse response = service.getActive();

        assertThat(response.code()).isEqualTo("APEUNI_V5");
        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    void getByPublicId_unknownTemplate_throwsNotFound() {
        UUID publicId = UUID.randomUUID();
        when(repository.findWithItemsByPublicId(publicId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByPublicId(publicId)).isInstanceOf(ScoreTemplateNotFoundException.class);
    }

    @Test
    void getByPublicId_returnsEvenIfRetired_noStatusFilter() {
        UUID publicId = UUID.randomUUID();
        ScoreTemplate retired = new ScoreTemplate();
        retired.setPublicId(publicId);
        retired.setCode("APEUNI_V5");
        retired.setVersion(1);
        retired.setName("APEUni V5");
        retired.setStatus(ScoreTemplateStatus.RETIRED);
        when(repository.findWithItemsByPublicId(publicId)).thenReturn(Optional.of(retired));

        ScoreTemplateResponse response = service.getByPublicId(publicId);

        assertThat(response.status()).isEqualTo("RETIRED");
    }
}
