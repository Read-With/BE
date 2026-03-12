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
@Tag(name = "Manifest", description = "Book manifest API")
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
            summary = "Get book manifest",
            description = "Returns the shared manifest payload used to bootstrap the reader."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Manifest returned successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Book not found or not accessible"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid request"
            )
    })
    public ApiResponse<ManifestResponseDTO> getBookManifest(
            @Parameter(description = "Book ID", required = true, example = "42")
            @PathVariable Long bookId) {

        Long userId = getCurrentUserId();
        ManifestResponseDTO response = manifestService.getBookManifest(bookId, userId);
        return ApiResponse.onSuccess(response);
    }
}
