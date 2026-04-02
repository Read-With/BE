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
            // Reader bootstrap과 locator 기반 읽기 흐름은 v2 계약으로 완전히 대체되었으므로
            // legacy /api 경로를 차단한다.
            Pattern.compile("^/api/books/[^/]+/manifest$"),
            Pattern.compile("^/api/progress(?:/.*)?$"),
            Pattern.compile("^/api/bookmarks(?:/.*)?$"),
            Pattern.compile("^/api/graph(?:/.*)?$"),
            Pattern.compile("^/api/admin(?:/.*)?$")
    );
    // 아래 legacy 경로는 아직 일부 클라이언트 전환이 끝나지 않아 의도적으로 열어둔다.
    // - /api/books, /api/books/{bookId}, POST /api/books
    //   이유: 프론트/운영에서 도서 목록/상세/업로드 진입점을 아직 /api로 호출할 수 있다.
    // - /api/favorites/**
    //   이유: 즐겨찾기 라우트의 v2 전환 공지가 끝났는지 운영 기준으로 재확인이 필요하다.
    // - /api/books/{bookId}/chapters/{chapterIdx}/pov-summaries
    //   이유: 챕터 POV summary는 최근에 v2 alias를 붙였고, 프론트 호출 경로를 더 확인한 뒤 막는 편이 안전하다.

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
