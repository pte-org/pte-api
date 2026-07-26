package com.pte.proctor.dto.response;

/** Sent to a STOMP client's {@code /user/queue/errors} when a mapped command fails. */
public record ErrorResponse(int status, String code) {
}
