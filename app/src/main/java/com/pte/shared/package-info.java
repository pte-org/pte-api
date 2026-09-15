/**
 * Cross-cutting infrastructure every business module may depend on: security,
 * web response envelopes, exception handling, audit, config.
 *
 * <p>Declared {@code OPEN} rather than left undeclared. Spring Modulith treats
 * every direct sub-package of the application's base package as a module by
 * default, {@code shared} included — a plain, unannotated package here would
 * still default to {@code CLOSED}, which only exposes types at the module's
 * own root and hides anything in a nested package. {@code shared} is nested
 * packages by design ({@code security/}, {@code web/}, {@code exception/}...),
 * so {@code OPEN} is what actually lets other modules reach
 * {@code shared.security.CurrentUser} etc. without a boundary violation.
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN,
        displayName = "Shared")
package com.pte.shared;
