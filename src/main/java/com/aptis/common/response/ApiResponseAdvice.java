package com.aptis.common.response;

import org.springframework.core.MethodParameter;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestControllerAdvice
public class ApiResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(
            MethodParameter returnType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {
        if (body == null
                || body instanceof ApiResponse<?>
                || body instanceof Resource
                || body instanceof byte[]
                || body instanceof StreamingResponseBody
                || body instanceof String
                || isSpringdocPath(request)
                || !MediaType.APPLICATION_JSON.isCompatibleWith(selectedContentType)) {
            return body;
        }

        HttpStatus status = resolveStatus(response);
        String path = request.getURI().getPath();

        if (body instanceof Page<?> page) {
            return ApiResponse.paged(
                    status,
                    "SUCCESS",
                    "Request completed successfully",
                    page,
                    path);
        }

        return ApiResponse.success(
                status,
                "SUCCESS",
                "Request completed successfully",
                body,
                path);
    }

    private HttpStatus resolveStatus(ServerHttpResponse response) {
        if (response instanceof ServletServerHttpResponse servletResponse) {
            return HttpStatus.valueOf(servletResponse.getServletResponse().getStatus());
        }
        return HttpStatus.OK;
    }

    private boolean isSpringdocPath(ServerHttpRequest request) {
        String path = request.getURI().getPath();
        return path.startsWith(ResponseConstants.API_DOCS_PREFIX)
                || path.startsWith(ResponseConstants.SWAGGER_UI_PREFIX);
    }
}
