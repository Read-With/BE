package com.kw.readwith.web.controller;

import com.kw.readwith.apiPayload.ApiResponse;
import com.kw.readwith.dto.graph.FineGraphResponseDTO;
import com.kw.readwith.dto.graph.MacroGraphResponseDTO;
import com.kw.readwith.service.FineGraphService;
import com.kw.readwith.service.MacroGraphService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/graph")
@RequiredArgsConstructor
@Tag(name = "Graph", description = "인물 관계 그래프 API")
public class GraphController {

    private final FineGraphService fineGraphService;
    private final MacroGraphService macroGraphService;

    /**
     * 세밀(이벤트) 그래프 조회
     */
    @GetMapping("/fine")
    @Operation(
            summary = "세밀(이벤트) 그래프 조회",
            description = "특정 이벤트에서의 인물 관계 그래프를 조회합니다. 해당 이벤트에 존재하는 EventRelationshipEdge를 기반으로 관계 데이터를 반환합니다."
    )
    public ApiResponse<FineGraphResponseDTO> getFineGraph(
            @Parameter(description = "책 ID", required = true, example = "1")
            @RequestParam Long bookId,
            @Parameter(description = "챕터 인덱스", required = true, example = "1")
            @RequestParam Integer chapterIdx,
            @Parameter(description = "이벤트 인덱스", required = true, example = "3")
            @RequestParam Integer eventIdx) {

        Long userId = 1L; // TODO: 실제 인증된 사용자 ID로 교체
        FineGraphResponseDTO response = fineGraphService.getFineGraph(bookId, chapterIdx, eventIdx, userId);
        return ApiResponse.onSuccess(response);
    }

    /**
     * 거시(챕터 누적) 그래프 조회
     */
    @GetMapping("/macro")
    @Operation(
            summary = "거시(챕터 누적) 그래프 조회",
            description = "특정 챕터까지의 누적 인물 관계 그래프를 조회합니다. ChapterRelationshipEdge 기반으로 챕터별 누적 관계를 반환합니다."
    )
    public ApiResponse<MacroGraphResponseDTO> getMacroGraph(
            @Parameter(description = "책 ID", required = true, example = "1")
            @RequestParam Long bookId,
            @Parameter(description = "조회할 마지막 챕터 인덱스", required = true, example = "3")
            @RequestParam Integer uptoChapter) {

        Long userId = 1L; // TODO: 실제 인증된 사용자 ID로 교체
        MacroGraphResponseDTO response = macroGraphService.getMacroGraph(bookId, uptoChapter, userId);
        return ApiResponse.onSuccess(response);
    }
}
