package com.pte.scoring.internal.repository;

/** Saved answer drafts of one examiner grading work — the raw rows the host's examiner progress rolls up. */
public record WorkDraftCount(Long workId, long count) {
}
