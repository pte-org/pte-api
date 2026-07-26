package com.pte.authoring.web;

import com.pte.common.exception.GlobalExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Registers the shared {@link GlobalExceptionHandler} within authoring's component scan. */
@RestControllerAdvice
public class AuthoringExceptionHandler extends GlobalExceptionHandler {
}
