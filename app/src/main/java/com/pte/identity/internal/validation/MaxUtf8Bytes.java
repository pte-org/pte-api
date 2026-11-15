package com.pte.identity.internal.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The text takes at most {@link #value()} bytes in UTF-8. A password is hashed with BCrypt, which refuses more than
 * 72 bytes; counting characters would let a Vietnamese password with accents (two or three bytes each) through to a
 * server error. {@code null} is valid, so {@code @NotBlank} stays in charge of "required".
 */
@Documented
@Constraint(validatedBy = MaxUtf8BytesValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxUtf8Bytes {

    int value();

    String message() default "Too long";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
