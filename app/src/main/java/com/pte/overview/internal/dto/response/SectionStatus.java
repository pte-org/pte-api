package com.pte.overview.internal.dto.response;

/** Outcome of computing one dashboard section; a failing or forbidden section never fails the page. */
public enum SectionStatus {
    OK,
    ERROR,
    FORBIDDEN
}
