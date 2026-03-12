package com.kw.readwith.config;

import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class V2ApiContractInterceptor implements HandlerInterceptor {

    private static final List<Pattern> LEGACY_TRANSITION_PATHS = List.of(
            Pattern.compile("^/api/books/[^/]+/manifest$"),
            Pattern.compile("^/api/progress(?:/.*)?$"),
            Pattern.compile("^/api/bookmarks(?:/.*)?$"),
            Pattern.compile("^/api/graph(?:/.*)?$"),
            Pattern.compile("^/api/admin(?:/.*)?$")
    );

    private final ApiContractProperties apiContractProperties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        if (!apiContractProperties.getResolvedMode().blocksLegacyRoutes()) {
            return true;
        }

        String requestUri = request.getRequestURI();
        if (!isLegacyTransitionPath(requestUri)) {
            return true;
        }

        throw new GeneralException(
                ErrorStatus._FORBIDDEN,
                "api.contract.mode=" + apiContractProperties.getResolvedMode().getPropertyValue()
                        + " 에서는 " + toV2Path(requestUri) + " 경로만 허용됩니다."
        );
    }

    private boolean isLegacyTransitionPath(String requestUri) {
        if (requestUri == null || requestUri.startsWith("/api/v2/")) {
            return false;
        }

        return LEGACY_TRANSITION_PATHS.stream().anyMatch(pattern -> pattern.matcher(requestUri).matches());
    }

    private String toV2Path(String requestUri) {
        if (requestUri == null || !requestUri.startsWith("/api/")) {
            return "/api/v2";
        }
        return "/api/v2" + requestUri.substring("/api".length());
    }
}
