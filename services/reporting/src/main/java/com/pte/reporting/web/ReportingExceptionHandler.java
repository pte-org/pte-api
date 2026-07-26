package com.pte.reporting.web;

import com.pte.common.exception.GlobalExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Registers the shared {@link GlobalExceptionHandler} within reporting's component scan. */
@RestControllerAdvice
public class ReportingExceptionHandler extends GlobalExceptionHandler {
}
