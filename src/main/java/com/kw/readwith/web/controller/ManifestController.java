package com.kw.readwith.web.controller;

import com.kw.readwith.apiPayload.ApiResponse;
import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.config.V2TransitionGuard;
import com.kw.readwith.dto.manifest.ManifestResponseDTO;
import com.kw.readwith.service.ManifestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "매니페스트", description = "리더 초기 진입에 필요한 도서 메타데이터 조회 API입니다.")
public class ManifestController {

    private final ManifestService manifestService;
    private final V2TransitionGuard transitionGuard;

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }
        if (authentication.getPrincipal() instanceof Long principal) {
            return principal;
        }
        if ("anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }
        throw new GeneralException(ErrorStatus._UNAUTHORIZED);
    }

    @ModelAttribute
    void validateTransitionRoute(HttpServletRequest request) {
        transitionGuard.ensureV2ManifestEnabled(request);
    }

    @GetMapping({"/api/books/{bookId}/manifest", "/api/v2/books/{bookId}/manifest"})
    @Operation(
            summary = "도서 매니페스트 조회",
            description = "리더 화면을 시작할 때 필요한 도서, 챕터, 이벤트, 인물, reader artifact 정보를 한 번에 조회합니다."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "매니페스트 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "도서를 찾을 수 없거나 아직 노출 가능한 상태가 아닙니다."
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청입니다."
            )
    })
    public ApiResponse<ManifestResponseDTO> getBookManifest(
            @Parameter(description = "조회할 도서 ID", required = true, example = "42")
            @PathVariable Long bookId) {

        Long userId = getCurrentUserId();
        ManifestResponseDTO response = manifestService.getBookManifest(bookId, userId);
        return ApiResponse.onSuccess(response);
    }
}
