package com.aptis.modules.examoperations.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "import_batch")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class ImportBatch {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false)
    private String hostId;

    // Nullable — exam is assigned later via FS2-07 (not at upload time)
    private Long examId;

    @Column(nullable = false)
    private String type; // STUDENT, TEACHER

    @Column(nullable = false)
    private String filename;

    @Column(nullable = false)
    private String status; // PENDING, PROCESSING, SUCCESS, FAILED

    private Integer totalRecords;

    private Integer processedRecords;

    private Integer validCount;

    private Integer errorCount;

    @Column(columnDefinition = "TEXT")
    private String errorsJson;

    private LocalDateTime createdAt;

    private LocalDateTime completedAt;

    /**
     * Factory method — creates a new batch in PENDING status.
     */
    public static ImportBatch create(String hostId, String type, String filename) {
        ImportBatch batch = new ImportBatch();
        batch.setHostId(hostId);
        batch.setType(type);
        batch.setFilename(filename);
        batch.setStatus(STATUS_PENDING);
        batch.setCreatedAt(LocalDateTime.now());
        return batch;
    }

    /**
     * Marks the batch as completed with provisioning results.
     */
    public void markCompleted(int validCount, int errorCount, String errorsJson) {
        this.validCount = validCount;
        this.errorCount = errorCount;
        this.errorsJson = errorsJson;
        this.processedRecords = validCount + errorCount;
        this.totalRecords = this.processedRecords;
        this.status = STATUS_SUCCESS;
        this.completedAt = LocalDateTime.now();
    }
}
