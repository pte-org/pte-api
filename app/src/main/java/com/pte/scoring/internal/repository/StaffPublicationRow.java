package com.pte.scoring.internal.repository;

import java.util.UUID;

/** Closed scalar projection: does not retain managed scoring entities or answer payloads. */
public interface StaffPublicationRow {
    UUID getSessionPublicId();
    UUID getPublicationPublicId();
}
