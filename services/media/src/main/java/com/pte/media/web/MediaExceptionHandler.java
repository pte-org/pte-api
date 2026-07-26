package com.pte.media.web;

import com.pte.common.exception.GlobalExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Registers the shared {@link GlobalExceptionHandler} within media's component scan. */
@RestControllerAdvice
public class MediaExceptionHandler extends GlobalExceptionHandler {
}
