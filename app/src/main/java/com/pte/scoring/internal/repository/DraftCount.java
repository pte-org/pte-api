package com.pte.scoring.internal.repository;

/** Saved answer drafts of one examiner grading assignment — the raw rows the host's examiner progress rolls up. */
public record DraftCount(Long assignmentId, long count) {
}
