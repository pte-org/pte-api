package com.pte.identity.internal.dto.request;

/** Account lifecycle transitions of a user. */
public enum UserStatusAction {
    SUSPEND, REACTIVATE, ARCHIVE, RESTORE
}
