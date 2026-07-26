package com.pte.scoring.web;

import com.pte.common.exception.GlobalExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Registers the shared {@link GlobalExceptionHandler} within scoring's component scan. */
@RestControllerAdvice
public class ScoringExceptionHandler extends GlobalExceptionHandler {
}
