package com.pte.scoring.internal.rubric.controller;

import com.pte.scoring.internal.constant.RubricConstants;
import com.pte.scoring.internal.exception.RubricVersionException;
import com.pte.scoring.internal.rubric.dto.RubricSimulationRequest;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;

/** Bound the raw body before parsing, including unknown fields/whitespace and requests without Content-Length. */
@ControllerAdvice(assignableTypes = RubricSimulationController.class)
public class RubricSimulationBodyAdvice extends RequestBodyAdviceAdapter {
    @Override
    public boolean supports(MethodParameter parameter, Type targetType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return targetType == RubricSimulationRequest.class;
    }

    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage message, MethodParameter parameter, Type targetType,
                                          Class<? extends HttpMessageConverter<?>> converterType) throws IOException {
        byte[] body = message.getBody().readNBytes(RubricConstants.MAX_SIMULATION_JSON_BYTES + 1);
        if (body.length > RubricConstants.MAX_SIMULATION_JSON_BYTES) {
            throw new RubricVersionException(HttpStatus.PAYLOAD_TOO_LARGE, RubricConstants.RUBRIC_PARAMETERS_TOO_LARGE);
        }
        return new HttpInputMessage() {
            @Override public InputStream getBody() { return new ByteArrayInputStream(body); }
            @Override public HttpHeaders getHeaders() { return message.getHeaders(); }
        };
    }
}
