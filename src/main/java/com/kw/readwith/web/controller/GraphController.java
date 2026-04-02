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
@Tag(name = "Graph", description = "?몃Ъ 愿怨?洹몃옒??API")
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
            summary = "?몃?(?대깽?? 洹몃옒??議고쉶",
            description = "locator ? chapterIdx/eventIdx 湲곕컲?쇰줈 ?대깽??洹몃옒?꾨? 議고쉶?⑸땲??"
    )
    public ApiResponse<FineGraphResponseDTO> getFineGraph(
            @Parameter(description = "梨?ID", required = true, example = "1")
            @RequestParam Long bookId,
            @Parameter(description = "legacy chapter index", example = "1")
            @RequestParam(required = false) Integer chapterIdx,
            @Parameter(description = "legacy event index", example = "3")
            @RequestParam(required = false) Integer eventIdx,
            @Parameter(description = "locator chapter index", example = "1")
            @RequestParam(required = false) Integer chapterIndex,
            @Parameter(description = "locator block index", example = "2")
            @RequestParam(required = false) Integer blockIndex,
            @Parameter(description = "locator offset", example = "5")
            @RequestParam(required = false) Integer offset) {

        Long userId = getCurrentUserId();
        LocatorDTO locator = buildLocator(chapterIndex, blockIndex, offset);
        FineGraphResponseDTO response = fineGraphService.getFineGraph(bookId, chapterIdx, eventIdx, locator, userId);
        return ApiResponse.onSuccess(response);
    }

    @GetMapping("/macro")
    @Operation(
            summary = "嫄곗떆(梨뺥꽣 ?꾩쟻) 洹몃옒??議고쉶",
            description = "uptoLocator ? uptoChapter 湲곕컲?쇰줈 ?꾩쟻 愿怨?洹몃옒?꾨? 議고쉶?⑸땲??"
    )
    public ApiResponse<MacroGraphResponseDTO> getMacroGraph(
            @Parameter(description = "梨?ID", required = true, example = "1")
            @RequestParam Long bookId,
            @Parameter(description = "legacy upto chapter", example = "3")
            @RequestParam(required = false) Integer uptoChapter,
            @Parameter(description = "locator chapter index", example = "3")
            @RequestParam(required = false) Integer chapterIndex,
            @Parameter(description = "locator block index", example = "2")
            @RequestParam(required = false) Integer blockIndex,
            @Parameter(description = "locator offset", example = "5")
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
