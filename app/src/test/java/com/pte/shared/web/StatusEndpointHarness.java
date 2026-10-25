package com.pte.shared.web;

import com.pte.shared.exception.GlobalExceptionHandler;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

/** Drives a controller's {@code PATCH .../status} route as a given caller, with the real error mapping. */
public final class StatusEndpointHarness implements AutoCloseable {

    private final MockedStatic<CurrentUserContext> context = Mockito.mockStatic(CurrentUserContext.class);
    private final MockMvc mvc;

    private StatusEndpointHarness(Object controller) {
        this.mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    public static StatusEndpointHarness of(Object controller) {
        return new StatusEndpointHarness(controller);
    }

    public static CurrentUser user(String... roles) {
        return new CurrentUser(UUID.randomUUID(), UUID.randomUUID(), List.of(roles));
    }

    public StatusEndpointHarness as(CurrentUser caller) {
        context.when(CurrentUserContext::required).thenReturn(caller);
        return this;
    }

    public ResultActions patchStatus(String path, String json, Object... uriVars) throws Exception {
        return mvc.perform(patch(path, uriVars).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Override
    public void close() {
        context.close();
    }
}
