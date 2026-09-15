/**
 * Exposed as a Spring Modulith named interface — {@code attempt} consumes
 * {@link com.pte.media.dto.response.PresignedDownloadResponse} through {@code
 * MediaService#presignGet} when pinning audio/image URLs. Default module
 * visibility only exposes the module's root package; this subpackage needs
 * the explicit opt-in to be a legal cross-module dependency target.
 */
@org.springframework.modulith.NamedInterface
package com.pte.media.dto.response;
