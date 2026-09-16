package com.pte.identity.domain;

/**
 * In {@code identity.domain}, not {@code identity.internal}: {@link
 * User#getStatus()} returns this type, and Spring Modulith's {@code verify()}
 * flags a public method returning an internal type as a boundary violation.
 */
public enum UserStatus {
    ACTIVE,
    SUSPENDED
}
