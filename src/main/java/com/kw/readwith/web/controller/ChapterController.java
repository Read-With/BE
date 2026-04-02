package com.kw.readwith.web.controller;

import com.kw.readwith.apiPayload.ApiResponse;
import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.dto.book.ChapterPovSummaryResponseDTO;
import com.kw.readwith.service.CharacterPovSummaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/books", "/api/v2/books"})
@RequiredArgsConstructor
@Tag(name = "챕터 POV 요약", description = "챕터별 인물 시점 요약 조회 API입니다.")
public class ChapterController {

    private final CharacterPovSummaryService characterPovSummaryService;

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Long) {
            return (Long) authentication.getPrincipal();
        }
        throw new GeneralException(ErrorStatus._UNAUTHORIZED);
    }

    @GetMapping("/{bookId}/chapters/{chapterIdx}/pov-summaries")
    @Operation(
            summary = "챕터 POV 요약 조회",
            description = "특정 챕터의 인물별 시점 요약을 조회합니다. 분석이 아직 완료되지 않았으면 빈 목록을 반환할 수 있습니다."
    )
    public ApiResponse<ChapterPovSummaryResponseDTO> getChapterPovSummaries(
            @Parameter(description = "조회할 도서 ID", required = true, example = "42")
            @PathVariable Long bookId,
            @Parameter(description = "조회할 챕터 인덱스(1-based)", required = true, example = "3")
            @PathVariable Integer chapterIdx) {

        Long userId = getCurrentUserId();
        ChapterPovSummaryResponseDTO response = characterPovSummaryService.getChapterPovSummaries(bookId, chapterIdx, userId);
        return ApiResponse.onSuccess(response);
    }
}
