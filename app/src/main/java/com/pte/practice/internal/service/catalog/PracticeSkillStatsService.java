package com.pte.practice.internal.service.catalog;

import com.pte.practice.internal.service.timing.PracticeTimingService;

import com.pte.itembank.PracticeQuestionService;
import com.pte.practice.internal.dto.response.PracticeCatalogResponse;
import com.pte.practice.internal.dto.response.PracticeCatalogSectionResponse;
import com.pte.practice.internal.dto.response.PracticeCatalogTaskResponse;
import com.pte.practice.internal.repository.PracticeResponseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Adds the per-skill question counts (Study-Pack "x of N answered") to a student catalog. */
@Service
public class PracticeSkillStatsService {

    private final PracticeQuestionService practiceQuestionService;
    private final PracticeResponseRepository itemRepository;

    public PracticeSkillStatsService(PracticeQuestionService practiceQuestionService,
            PracticeResponseRepository itemRepository) {
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
        Map<String, Integer> averages = new HashMap<>();
        for (Object[] row : itemRepository.averageScoreByTaskCode(studentPublicId)) {
            if (row[1] != null) {
                averages.put(PracticeTimingService.normalize((String) row[0]),
                        BigDecimal.valueOf(((Number) row[1]).doubleValue()).setScale(0, RoundingMode.HALF_UP).intValue());
            }
        }
        List<PracticeCatalogSectionResponse> sections = catalog.sections().stream()
                .map(section -> new PracticeCatalogSectionResponse(section.code(), section.displayName(),
                        section.taskTypes().stream().map(task -> {
                            String code = PracticeTimingService.normalize(task.code());
                            return task.withStats(answered.getOrDefault(code, 0L), averages.get(code));
                        }).toList(),
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
