/**
 * Exposed as a Spring Modulith named interface — {@code assessment} reuses
 * these enums directly (task taxonomy is itembank's own concept; assessment's
 * {@code SnapshotItem}/{@code BlueprintItem} store them, they don't redefine
 * them). Default module visibility only exposes the module's root package;
 * this subpackage needs the explicit opt-in to be a legal cross-module
 * dependency target.
 */
@org.springframework.modulith.NamedInterface
package com.pte.itembank.domain.enums;
