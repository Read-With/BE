package com.kw.readwith.config;

import com.kw.readwith.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class V2ApiContractInterceptorTest {

    private final ApiContractProperties apiContractProperties = new ApiContractProperties();
    private final V2ApiContractInterceptor interceptor = new V2ApiContractInterceptor(apiContractProperties);
    private final HandlerMethod handlerMethod = buildHandlerMethod();

    @Test
    @DisplayName("v2_only에서는 manifest/progress/bookmark/graph/admin legacy 경로를 차단한다")
    void blocksSafeLegacyRoutesWhenV2Only() {
        apiContractProperties.setMode("v2_only");

        assertBlocked("/api/books/1/manifest");
        assertBlocked("/api/progress/1");
        assertBlocked("/api/bookmarks");
        assertBlocked("/api/graph/fine");
        assertBlocked("/api/admin/books/1/events");
    }

    @Test
    @DisplayName("아직 전환 확인이 필요한 legacy books/favorites/pov-summary 경로는 열어둔다")
    void leavesTransitionalLegacyRoutesOpen() {
        apiContractProperties.setMode("v2_only");

        assertAllowed("/api/books");
        assertAllowed("/api/books/1");
        assertAllowed("/api/favorites");
        assertAllowed("/api/books/1/chapters/2/pov-summaries");
    }

    @Test
    @DisplayName("prepare 모드에서는 legacy 경로를 차단하지 않는다")
    void doesNotBlockLegacyRoutesInPrepareMode() {
        apiContractProperties.setMode("prepare");

        assertAllowed("/api/books/1/manifest");
        assertAllowed("/api/progress/1");
    }

    @Test
    @DisplayName("dev swagger UI origin??epub upload CORS??허용한다")
    void allowsDevSwaggerUiOriginForUploadCors() {
        SecurityConfig securityConfig = new SecurityConfig(null, null, null);
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v2/books");
        request.addHeader("Origin", "https://dev.readwith.store");

        CorsConfiguration corsConfiguration = securityConfig.corsConfigurationSource().getCorsConfiguration(request);

        assertThat(corsConfiguration).isNotNull();
        assertThat(corsConfiguration.checkOrigin("https://dev.readwith.store"))
                .isEqualTo("https://dev.readwith.store");
    }

    private void assertBlocked(String requestUri) {
        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> interceptor.preHandle(request(requestUri), new MockHttpServletResponse(), handlerMethod)
        );
        assertThat(exception.getErrorReason().getMessage()).contains("/api/v2");
    }

    private void assertAllowed(String requestUri) {
        assertDoesNotThrow(() -> interceptor.preHandle(request(requestUri), new MockHttpServletResponse(), handlerMethod));
    }

    private MockHttpServletRequest request(String requestUri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", requestUri);
        request.setRequestURI(requestUri);
        return request;
    }

    private HandlerMethod buildHandlerMethod() {
        try {
            Method method = DummyHandler.class.getDeclaredMethod("handle");
            return new HandlerMethod(new DummyHandler(), method);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    private static class DummyHandler {
        @SuppressWarnings("unused")
        public void handle() {
        }
    }
}
