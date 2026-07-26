package com.pte.scheduling.web;

import com.pte.common.exception.GlobalExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Registers the shared {@link GlobalExceptionHandler} within scheduling's component scan. */
@RestControllerAdvice
public class SchedulingExceptionHandler extends GlobalExceptionHandler {
}
