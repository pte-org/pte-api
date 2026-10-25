package com.pte.practice.internal.service;

import com.pte.itembank.PracticeQuestionService;
import com.pte.practice.internal.dto.response.PracticeCatalogResponse;
import com.pte.practice.internal.dto.response.PracticeCatalogSectionResponse;
import com.pte.practice.internal.dto.response.PracticeCatalogTaskResponse;
import com.pte.practice.internal.repository.PracticeSessionItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Adds the per-skill question counts (Study-Pack "x of N answered") to a student catalog. */
@Service
public class PracticeSkillStatsService {

    private final PracticeQuestionService practiceQuestionService;
    private final PracticeSessionItemRepository itemRepository;

    public PracticeSkillStatsService(PracticeQuestionService practiceQuestionService,
            PracticeSessionItemRepository itemRepository) {
        this.practiceQuestionService = practiceQuestionService;
        this.itemRepository = itemRepository;
    }

    @Transactional(readOnly = true)
    public PracticeCatalogResponse withCounts(PracticeCatalogResponse catalog, UUID studentPublicId) {
        List<String> allCodes = catalog.sections().stream()
                .flatMap(section -> section.taskTypes().stream()).map(PracticeCatalogTaskResponse::code).toList();
        Map<String, Long> available = practiceQuestionService.countPublishedByTaskTypeKeys(allCodes);
        Map<String, Long> answered = new HashMap<>();
        for (Object[] row : itemRepository.countAnsweredSourceQuestionsByTaskCode(studentPublicId)) {
            answered.put(PracticeTimingService.normalize((String) row[0]), ((Number) row[1]).longValue());
        }
        List<PracticeCatalogSectionResponse> sections = catalog.sections().stream()
                .map(section -> section.withQuestionCounts(
                        sum(section, available), sum(section, answered)))
                .toList();
        return new PracticeCatalogResponse(catalog.productCode(), catalog.title(), catalog.catalogVersion(),
                catalog.timeLimitSeconds(), sections);
    }

    private long sum(PracticeCatalogSectionResponse section, Map<String, Long> countsByCode) {
        return section.taskTypes().stream()
                .mapToLong(task -> countsByCode.getOrDefault(PracticeTimingService.normalize(task.code()), 0L)).sum();
    }
}
