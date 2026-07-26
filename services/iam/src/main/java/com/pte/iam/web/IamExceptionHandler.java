package com.pte.iam.web;

import com.pte.common.exception.GlobalExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Registers the shared {@link GlobalExceptionHandler} within iam's component scan
 * (pte-common is not scanned by default). Empty by design — service-specific
 * handlers, if ever needed, are added here.
 */
@RestControllerAdvice
public class IamExceptionHandler extends GlobalExceptionHandler {
}
