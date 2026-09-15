package com.pte.admin.service;

import com.pte.admin.client.StudentExportClient;
import com.pte.admin.client.dto.StudentExportItem;
import com.pte.admin.repository.StudentRosterEntryRepository;
import com.pte.common.web.ExportPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/** Bounded, resumable rebuild of Admin's student roster projection. */
@Service
public class StudentRosterRebuildService {

    private static final Logger log = LoggerFactory.getLogger(StudentRosterRebuildService.class);

    private final StudentExportClient studentExportClient;
    private final StudentRosterEntryRepository rosterRepository;

    public StudentRosterRebuildService(StudentExportClient studentExportClient,
            StudentRosterEntryRepository rosterRepository) {
        this.studentExportClient = studentExportClient;
        this.rosterRepository = rosterRepository;
    }

    public RebuildSummary rebuildAll() {
        Instant startedAt = Instant.now();
        int rows = 0;
        String cursor = null;
        boolean hasMore;
        do {
            ExportPage<StudentExportItem> page = studentExportClient.exportStudents(null, cursor);
            page.items().forEach(this::upsert);
            rows += page.items().size();
            cursor = page.nextCursor();
            hasMore = page.hasMore() && cursor != null;
        } while (hasMore);

        long durationMs = Duration.between(startedAt, Instant.now()).toMillis();
        log.info("Student roster rebuild complete: {} rows applied in {} ms", rows, durationMs);
        return new RebuildSummary(rows, durationMs);
    }

    private void upsert(StudentExportItem item) {
        rosterRepository.upsert(item.studentPublicId(), item.tenantId(), item.email(), item.fullName(),
                item.studentCode(), item.phone(), item.status(), item.createdAt());
    }

    public record RebuildSummary(int rowsApplied, long durationMs) {
    }
}
