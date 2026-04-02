package com.kw.readwith.web.controller;

import com.kw.readwith.apiPayload.ApiResponse;
import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.dto.common.LocatorDTO;
import com.kw.readwith.dto.graph.FineGraphResponseDTO;
import com.kw.readwith.dto.graph.MacroGraphResponseDTO;
import com.kw.readwith.service.FineGraphService;
import com.kw.readwith.service.MacroGraphService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/graph", "/api/v2/graph"})
@RequiredArgsConstructor
@Tag(name = "관계 그래프", description = "인물 관계 그래프 조회 API입니다.")
public class GraphController {

    private final FineGraphService fineGraphService;
    private final MacroGraphService macroGraphService;

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Long) {
            return (Long) authentication.getPrincipal();
        }
        throw new GeneralException(ErrorStatus._UNAUTHORIZED);
    }

    @GetMapping("/fine")
    @Operation(
            summary = "이벤트 단위 그래프 조회",
            description = "특정 이벤트 시점의 관계 그래프를 조회합니다. `locator` 또는 legacy `chapterIdx/eventIdx` 조합 중 하나를 사용합니다."
    )
    public ApiResponse<FineGraphResponseDTO> getFineGraph(
            @Parameter(description = "조회할 도서 ID", required = true, example = "1")
            @RequestParam Long bookId,
            @Parameter(description = "legacy 챕터 인덱스. locator를 쓰지 않을 때만 사용합니다.", example = "1")
            @RequestParam(required = false) Integer chapterIdx,
            @Parameter(description = "legacy 이벤트 인덱스. locator를 쓰지 않을 때만 사용합니다.", example = "3")
            @RequestParam(required = false) Integer eventIdx,
            @Parameter(description = "locator의 챕터 인덱스", example = "1")
            @RequestParam(required = false) Integer chapterIndex,
            @Parameter(description = "locator의 블록 인덱스", example = "2")
            @RequestParam(required = false) Integer blockIndex,
            @Parameter(description = "locator의 블록 내부 offset", example = "5")
            @RequestParam(required = false) Integer offset) {

        Long userId = getCurrentUserId();
        LocatorDTO locator = buildLocator(chapterIndex, blockIndex, offset);
        FineGraphResponseDTO response = fineGraphService.getFineGraph(bookId, chapterIdx, eventIdx, locator, userId);
        return ApiResponse.onSuccess(response);
    }

    @GetMapping("/macro")
    @Operation(
            summary = "누적 그래프 조회",
            description = "사용자가 특정 위치까지 읽었을 때의 누적 관계 그래프를 조회합니다. `uptoLocator` 또는 legacy `uptoChapter`를 사용할 수 있습니다."
    )
    public ApiResponse<MacroGraphResponseDTO> getMacroGraph(
            @Parameter(description = "조회할 도서 ID", required = true, example = "1")
            @RequestParam Long bookId,
            @Parameter(description = "legacy 누적 기준 챕터. locator를 쓰지 않을 때만 사용합니다.", example = "3")
            @RequestParam(required = false) Integer uptoChapter,
            @Parameter(description = "locator의 챕터 인덱스", example = "3")
            @RequestParam(required = false) Integer chapterIndex,
            @Parameter(description = "locator의 블록 인덱스", example = "2")
            @RequestParam(required = false) Integer blockIndex,
            @Parameter(description = "locator의 블록 내부 offset", example = "5")
            @RequestParam(required = false) Integer offset) {

        Long userId = getCurrentUserId();
        LocatorDTO uptoLocator = buildLocator(chapterIndex, blockIndex, offset);
        MacroGraphResponseDTO response = macroGraphService.getMacroGraph(bookId, uptoChapter, uptoLocator, userId);
        return ApiResponse.onSuccess(response);
    }

    private LocatorDTO buildLocator(Integer chapterIndex, Integer blockIndex, Integer offset) {
        if (chapterIndex == null && blockIndex == null && offset == null) {
            return null;
        }
        if (chapterIndex == null || blockIndex == null || offset == null) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "chapterIndex, blockIndex, offset must be provided together.");
        }

        return LocatorDTO.builder()
                .chapterIndex(chapterIndex)
                .blockIndex(blockIndex)
                .offset(offset)
                .build();
    }
}
