package com.pte.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Ledger row recording that a named one-time projection backfill has run. */
@Entity
@Table(name = "projection_backfills")
public class ProjectionBackfill {

    @Id
    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "claimed_at", nullable = false)
    private Instant claimedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "rows_applied")
    private Integer rowsApplied;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Instant getClaimedAt() {
        return claimedAt;
    }

    public void setClaimedAt(Instant claimedAt) {
        this.claimedAt = claimedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Integer getRowsApplied() {
        return rowsApplied;
    }

    public void setRowsApplied(Integer rowsApplied) {
        this.rowsApplied = rowsApplied;
    }
}
