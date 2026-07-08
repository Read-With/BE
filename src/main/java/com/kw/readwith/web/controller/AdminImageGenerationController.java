package com.kw.readwith.web.controller;

import com.kw.readwith.apiPayload.ApiResponse;
import com.kw.readwith.dto.admin.AdminImageGenerationStatusResponseDTO;
import com.kw.readwith.service.AdminImageGenerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/admin/image-generation")
@Tag(
        name = "관리자 이미지 생성",
        description = """
                책 단위 캐릭터 이미지 생성 콘솔 API입니다.
                관리자는 상태 조회 -> 대표 캐릭터 후보사진 4장 생성 -> 후보 1장 선택 -> 개별 캐릭터 재생성 순서로 사용합니다.
                구버전 후보/승인/fan-out API를 직접 조합하지 않고, 이 태그의 4개 API만으로 관리자 페이지 플로우를 구성합니다.
                """
)
public class AdminImageGenerationController {

    private final AdminImageGenerationService adminImageGenerationService;

    @Operation(
            summary = "책 이미지 생성 상태 조회",
            description = """
                    관리자 페이지가 책 상세 진입 시 가장 먼저 호출하는 상태 조회 API입니다.
                    서버가 자동 지정한 대표 캐릭터, 책에 저장된 대표 후보사진 4장, 선택된 후보사진, 캐릭터별 현재 이미지 상태를 함께 반환합니다.
                    status는 EMPTY, REFERENCE_GENERATING, REFERENCE_READY, FANOUT_GENERATING, READY, FAILED 중 하나이며 nextAction으로 다음 버튼 동작을 판단할 수 있습니다.
                    이미지 URL이 null이면 아직 생성 중이거나 실패한 상태이므로 failureCode와 imageStatus를 함께 표시해야 합니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "책 단위 이미지 생성 콘솔 상태 조회 성공",
                    content = @Content(schema = @Schema(implementation = AdminImageGenerationStatusResponseDTO.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "PROGRESS4001: 해당 책을 찾을 수 없습니다."
            )
    })
    @GetMapping("/books/{bookId}")
    public ApiResponse<AdminImageGenerationStatusResponseDTO> getBookImageGenerationStatus(
            @Parameter(description = "이미지 생성 상태를 조회할 책 ID", required = true, example = "1")
            @PathVariable Long bookId) {
        AdminImageGenerationStatusResponseDTO response = adminImageGenerationService.getBookStatus(bookId);
        return ApiResponse.onSuccess(response);
    }

    @Operation(
            summary = "대표 캐릭터 후보사진 4장 생성",
            description = """
                    서버가 책의 주요 캐릭터를 우선으로 대표 캐릭터를 자동 지정하고, 해당 캐릭터의 후보사진 4장을 생성합니다.
                    책에는 slotNo 1~4 후보만 유지되며, 이미 후보가 있으면 같은 slot을 덮어써서 관리자 페이지에는 항상 최대 4장만 노출됩니다.
                    이 API는 나머지 캐릭터 이미지를 생성하지 않습니다. 관리자가 후보 1장을 선택해야 fan-out 생성이 시작되어 비용 낭비를 줄입니다.
                    일부 slot 생성이 실패하면 성공한 후보는 READY로 반환하고 실패 slot은 FAILED 및 failureCode=REFERENCE_GENERATION_FAILED로 반환합니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "대표 캐릭터 후보사진 4장 생성 처리 성공. 개별 생성 실패는 응답의 후보 status/failureCode로 확인합니다.",
                    content = @Content(schema = @Schema(implementation = AdminImageGenerationStatusResponseDTO.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "ADMIN4026: 대표 후보사진을 만들 캐릭터가 없습니다."
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "PROGRESS4001: 해당 책을 찾을 수 없습니다."
            )
    })
    @PostMapping("/books/{bookId}/reference-candidates")
    public ApiResponse<AdminImageGenerationStatusResponseDTO> generateReferenceCandidates(
            @Parameter(description = "대표 캐릭터 후보사진을 생성할 책 ID", required = true, example = "1")
            @PathVariable Long bookId) {
        AdminImageGenerationStatusResponseDTO response = adminImageGenerationService.generateReferenceCandidates(bookId);
        return ApiResponse.onSuccess(response);
    }

    @Operation(
            summary = "대표 후보사진 1장 선택 및 전체 fan-out",
            description = """
                    관리자가 대표 후보사진 4장 중 마음에 드는 1장을 선택할 때 호출합니다.
                    선택된 후보는 책의 active reference image가 되고, 대표 캐릭터의 게시 이미지로 즉시 반영됩니다.
                    이후 서버가 같은 reference image를 입력 이미지로 사용해 나머지 모든 캐릭터 이미지를 fan-out 생성합니다.
                    후보가 생성 완료 상태가 아니거나 다른 책의 asset이면 ADMIN4021 또는 ADMIN4022 오류가 발생합니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "대표 후보 선택 및 나머지 캐릭터 fan-out 처리 성공. 개별 실패는 characters[].imageStatus/failureCode로 확인합니다.",
                    content = @Content(schema = @Schema(implementation = AdminImageGenerationStatusResponseDTO.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "ADMIN4021: 후보사진이 요청한 책에 속하지 않습니다. ADMIN4022: 선택 가능한 대표 후보사진 상태가 아닙니다."
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "PROGRESS4001: 해당 책을 찾을 수 없습니다."
            )
    })
    @PostMapping("/books/{bookId}/reference-candidates/{candidateId}/select")
    public ApiResponse<AdminImageGenerationStatusResponseDTO> selectReferenceCandidate(
            @Parameter(description = "대표 후보사진을 선택할 책 ID", required = true, example = "1")
            @PathVariable Long bookId,
            @Parameter(description = "선택할 대표 후보사진 asset ID", required = true, example = "101")
            @PathVariable Long candidateId) {
        AdminImageGenerationStatusResponseDTO response = adminImageGenerationService.selectReferenceCandidate(bookId, candidateId);
        return ApiResponse.onSuccess(response);
    }

    @Operation(
            summary = "개별 캐릭터 이미지 재생성",
            description = """
                    fan-out 이후 특정 캐릭터 이미지가 마음에 들지 않을 때 그 캐릭터 1명만 다시 생성합니다.
                    책에 선택된 대표 후보사진(active reference image)이 있어야 하며, 기존 대표 후보사진을 입력 이미지로 재사용합니다.
                    대표 캐릭터 본인의 이미지는 이 API로 재생성하지 않습니다. 대표 후보사진 4장을 다시 생성한 뒤 새 후보를 선택해야 합니다.
                    성공 시 해당 캐릭터의 게시 이미지 경로를 덮어쓰고, 실패 시 imageStatus=FAILED 및 failureCode=REFERENCE_EDIT_FAILED로 반환합니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "개별 캐릭터 이미지 재생성 처리 성공. 생성 실패는 응답의 characters[].imageStatus/failureCode로 확인합니다.",
                    content = @Content(schema = @Schema(implementation = AdminImageGenerationStatusResponseDTO.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "ADMIN4022: 대표 캐릭터는 후보 4장 재생성 후 다시 선택해야 합니다. ADMIN4023: 선택된 대표 후보사진이 없습니다."
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "PROGRESS4001: 해당 책을 찾을 수 없습니다. ADMIN4004: 해당 캐릭터를 찾을 수 없습니다. ADMIN4006: 캐릭터가 해당 책에 속하지 않습니다."
            )
    })
    @PostMapping("/books/{bookId}/characters/{characterId}/regenerate")
    public ApiResponse<AdminImageGenerationStatusResponseDTO> regenerateCharacterImage(
            @Parameter(description = "캐릭터 이미지가 속한 책 ID", required = true, example = "1")
            @PathVariable Long bookId,
            @Parameter(description = "다시 생성할 캐릭터 DB ID", required = true, example = "11")
            @PathVariable Long characterId) {
        AdminImageGenerationStatusResponseDTO response = adminImageGenerationService.regenerateCharacterImage(bookId, characterId);
        return ApiResponse.onSuccess(response);
    }
}
