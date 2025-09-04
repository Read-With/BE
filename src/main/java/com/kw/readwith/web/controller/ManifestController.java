package com.kw.readwith.web.controller;

import com.kw.readwith.apiPayload.ApiResponse;
import com.kw.readwith.dto.manifest.ManifestResponseDTO;
import com.kw.readwith.service.ManifestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
@Tag(name = "Manifest", description = "책 구조 패키지 API")
public class ManifestController {

    private final ManifestService manifestService;

    /**
     * 책 구조/캐시 패키지 조회
     * 책 메타데이터 + 챕터/이벤트 + 인물 정보를 한 번에 제공하여 뷰어 초기화를 돕습니다.
     * 개인화 데이터(진도, 즐겨찾기, 북마크)는 별도 API에서 조회해야 합니다.
     */
    @GetMapping("/{bookId}/manifest")
    @Operation(
        summary = "책 구조 패키지 조회",
        description = "책 메타데이터, 챕터/이벤트 구조, 인물 정보를 한 번에 제공합니다. " +
                     "뷰어 초기화에 필요한 공용 데이터를 포함하며, " +
                     "개인화 데이터(진도, 즐겨찾기 등)는 별도 API에서 조회해야 합니다."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "책 구조 패키지 조회 성공"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", 
            description = "책을 찾을 수 없거나 접근 권한이 없음"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400", 
            description = "잘못된 요청 파라미터"
        )
    })
    public ApiResponse<ManifestResponseDTO> getBookManifest(
            @Parameter(
                description = "책 ID", 
                required = true,
                example = "42"
            )
            @PathVariable Long bookId) {
        
        // TODO: 실제 인증된 사용자 ID 가져오기
        // 현재는 임시로 하드코딩된 값 사용
        Long userId = 1L; // JWT에서 추출해야 함
        
        ManifestResponseDTO response = manifestService.getBookManifest(bookId, userId);
        return ApiResponse.onSuccess(response);
    }
}
