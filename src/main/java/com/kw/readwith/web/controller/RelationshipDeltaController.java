package com.kw.readwith.web.controller;

import com.kw.readwith.apiPayload.ApiResponse;
import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.dto.relationship.RelationshipDeltaListResponseDTO;
import com.kw.readwith.dto.relationship.RelationshipGraphResponseDTO;
import com.kw.readwith.service.RelationshipDeltaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/books")
@Tag(name = "Relationship Delta", description = "relationship delta raw/fold 조회 API입니다.")
public class RelationshipDeltaController {

    private final RelationshipDeltaService relationshipDeltaService;

    @GetMapping("/{bookId}/relationship-deltas")
    @Operation(summary = "relationship delta 원본 조회")
    public ApiResponse<RelationshipDeltaListResponseDTO> getRelationshipDeltas(
            @Parameter(description = "조회 대상 도서 ID", required = true) @PathVariable Long bookId,
            @RequestParam(required = false) Integer chapterIndex,
            @RequestParam(required = false) String eventId,
            @RequestParam(required = false) String fromEventId,
            @RequestParam(required = false) String toEventId) {
        RelationshipDeltaListResponseDTO response = relationshipDeltaService.getRelationshipDeltas(
                bookId,
                chapterIndex,
                eventId,
                fromEventId,
                toEventId,
                getCurrentUserIdOrNull()
        );
        return ApiResponse.onSuccess(response);
    }

    @GetMapping("/{bookId}/relationship-graph")
    @Operation(summary = "relationship delta 누적 graph 조회")
    public ApiResponse<RelationshipGraphResponseDTO> getRelationshipGraph(
            @Parameter(description = "조회 대상 도서 ID", required = true) @PathVariable Long bookId,
            @RequestParam(required = false, defaultValue = "book") String scope,
            @RequestParam(required = false) Integer chapterIndex,
            @RequestParam(required = false) String eventId,
            @RequestParam(required = false) Integer blockIndex,
            @RequestParam(required = false) Integer offset) {
        RelationshipGraphResponseDTO response = relationshipDeltaService.getRelationshipGraph(
                bookId,
                scope,
                chapterIndex,
                eventId,
                blockIndex,
                offset,
                getCurrentUserIdOrNull()
        );
        return ApiResponse.onSuccess(response);
    }

    private Long getCurrentUserIdOrNull() {
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
}
