package tn.oliveconnect.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class RequestContextFilterTest {

    @AfterEach
    void clearSecurityAndLoggingContext() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    void requestContextContainsTraceEndpointAndAuthenticatedUserDuringTheRequest() throws Exception {
        RequestContextFilter filter = new RequestContextFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orchards");
        request.addHeader("X-Trace-Id", "trace-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("user-42", null, "ROLE_FARMER"));
        AtomicReference<Map<String, String>> contextDuringRequest = new AtomicReference<>();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
            contextDuringRequest.set(new HashMap<>(MDC.getCopyOfContextMap()));
        });

        assertThat(contextDuringRequest).hasValueSatisfying(context -> assertThat(context)
                .containsEntry("traceId", "trace-123")
                .containsEntry("userId", "user-42")
                .containsEntry("endpoint", "GET /api/orchards"));
        assertThat(response.getHeader("X-Trace-Id")).isEqualTo("trace-123");
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void requestContextGeneratesTraceAndUsesAnonymousForUnauthenticatedRequest() throws Exception {
        RequestContextFilter filter = new RequestContextFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Map<String, String>> contextDuringRequest = new AtomicReference<>();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
            contextDuringRequest.set(new HashMap<>(MDC.getCopyOfContextMap()));
        });

        String traceId = contextDuringRequest.get().get("traceId");
        assertThatCode(() -> UUID.fromString(traceId)).doesNotThrowAnyException();
        assertThat(contextDuringRequest.get())
                .containsEntry("userId", "anonymous")
                .containsEntry("endpoint", "POST /api/auth/login");
        assertThat(response.getHeader("X-Trace-Id")).isEqualTo(traceId);
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }
}
