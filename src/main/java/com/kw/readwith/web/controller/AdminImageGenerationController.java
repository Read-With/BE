package com.kw.readwith.web.controller;

import com.kw.readwith.apiPayload.ApiResponse;
import com.kw.readwith.dto.admin.AdminImageGenerationStatusResponseDTO;
import com.kw.readwith.dto.admin.ProcessingJobLogResponseDTO;
import com.kw.readwith.dto.admin.ProcessingJobResponseDTO;
import com.kw.readwith.service.AdminImageGenerationService;
import com.kw.readwith.service.CharacterImageFanoutJobDispatcher;
import com.kw.readwith.service.CharacterImageFanoutJobService;
import com.kw.readwith.domain.enums.ProcessingJobStatus;
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

import java.util.List;

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
    private final CharacterImageFanoutJobService fanoutJobService;
    private final CharacterImageFanoutJobDispatcher fanoutJobDispatcher;

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
                    responseCode = "409",
                    description = "ADMIN4028: 해당 책의 fan-out job이 진행 중이라 후보를 다시 생성할 수 없습니다."
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
            summary = "대표 후보사진 1장 선택 및 Batch fan-out 등록",
            description = """
                    관리자가 대표 후보사진 4장 중 마음에 드는 1장을 선택할 때 호출합니다.
                    candidateId에는 상태 조회 또는 후보 생성 응답의 referenceCandidates[].id에 담긴 asset DB ID를 전달합니다.
                    referenceCandidates[].slotNo(1~4)는 화면 표시용 슬롯 번호이며 candidateId로 사용할 수 없습니다.
                    선택된 후보는 책의 active reference image가 되고, 대표 캐릭터의 게시 이미지로 즉시 반영됩니다.
                    이후 서버는 선택된 한 장을 canonical reference로 고정하고, 같은 reference URL과 GPT Image 2 series-lock 프롬프트를 사용하는 OpenAI Batch job을 등록합니다.
                    응답의 fanoutJob.status=QUEUED는 등록 대기 상태이며 이미지 생성 완료를 뜻하지 않습니다. fanoutJob.status=READY가 되어야 모든 대상 이미지의 DB/S3 반영이 끝난 상태입니다.
                    후보가 생성 완료 상태가 아니거나 다른 책의 asset이면 ADMIN4021 또는 ADMIN4022 오류가 발생합니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "대표 후보 선택 및 fan-out Batch job 큐 등록 성공. fanoutJob.id로 상태와 로그를 조회합니다.",
                    content = @Content(schema = @Schema(implementation = AdminImageGenerationStatusResponseDTO.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "ADMIN4021: 후보사진이 요청한 책에 속하지 않습니다. ADMIN4022: 선택 가능한 대표 후보사진 상태가 아닙니다."
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "ADMIN4028: 해당 책의 fan-out job이 이미 진행 중입니다."
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
            @Parameter(
                    description = "선택할 대표 후보사진의 asset DB ID. referenceCandidates[].id 값을 전달하며 slotNo(1~4)가 아닙니다.",
                    required = true,
                    example = "101"
            )
            @PathVariable Long candidateId) {
        AdminImageGenerationStatusResponseDTO response = adminImageGenerationService.selectReferenceCandidate(bookId, candidateId);
        ProcessingJobResponseDTO fanoutJob = response.getFanoutJob();
        if (fanoutJob != null && fanoutJob.getStatus() == ProcessingJobStatus.QUEUED) {
            fanoutJobDispatcher.dispatch(fanoutJob.getId());
        }
        return ApiResponse.onSuccess(response);
    }

    @Operation(
            summary = "캐릭터 이미지 fan-out job 조회",
            description = "대표 후보 선택으로 등록된 OpenAI Batch fan-out job의 처리 상태와 외부 Batch/file ID를 조회합니다. status=READY여야 모든 대상 이미지의 게시가 완료된 상태입니다."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "fan-out job 조회 성공",
                    content = @Content(schema = @Schema(implementation = ProcessingJobResponseDTO.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "ADMIN4029: 캐릭터 이미지 fan-out job을 찾을 수 없습니다."
            )
    })
    @GetMapping("/fanout-jobs/{jobId}")
    public ApiResponse<ProcessingJobResponseDTO> getFanoutJob(
            @Parameter(description = "상태를 조회할 fan-out job ID", required = true, example = "123")
            @PathVariable Long jobId) {
        return ApiResponse.onSuccess(fanoutJobService.getFanoutJob(jobId));
    }

    @Operation(
            summary = "캐릭터 이미지 fan-out job 로그 조회",
            description = "OpenAI Batch 등록, 상태 변경, 캐릭터별 게시 또는 실패 내역을 순서대로 조회합니다."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "fan-out job 로그 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "ADMIN4029: 캐릭터 이미지 fan-out job을 찾을 수 없습니다."
            )
    })
    @GetMapping("/fanout-jobs/{jobId}/logs")
    public ApiResponse<List<ProcessingJobLogResponseDTO>> getFanoutJobLogs(
            @Parameter(description = "로그를 조회할 fan-out job ID", required = true, example = "123")
            @PathVariable Long jobId) {
        return ApiResponse.onSuccess(fanoutJobService.getFanoutJobLogs(jobId));
    }

    @Operation(
            summary = "실패한 fan-out job의 Batch 결과 게시 재시도",
            description = """
                    OpenAI Batch는 완료됐지만 S3 또는 DB 게시 단계에서 실패한 job의 기존 output/error 파일을 다시 적용합니다.
                    새로운 OpenAI Batch나 이미지 생성 요청을 제출하지 않으므로 이미지 생성 비용이 다시 발생하지 않습니다.
                    status=FAILED이고 outputFileId 또는 errorFileId가 저장된 job만 재시도할 수 있습니다.
                    응답의 status=READY여야 모든 대상 이미지 게시가 완료된 상태입니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "기존 Batch 결과 게시 재시도 완료",
                    content = @Content(schema = @Schema(implementation = ProcessingJobResponseDTO.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "ADMIN4030: 재시도할 수 없는 job 상태입니다. ADMIN4031: 저장된 Batch 결과 파일이 없습니다."
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "ADMIN4029: 캐릭터 이미지 fan-out job을 찾을 수 없습니다."
            )
    })
    @PostMapping("/fanout-jobs/{jobId}/retry-publish")
    public ApiResponse<ProcessingJobResponseDTO> retryFanoutResultPublish(
            @Parameter(description = "결과 게시를 다시 시도할 실패한 fan-out job ID", required = true, example = "123")
            @PathVariable Long jobId) {
        fanoutJobService.retryResultApplication(jobId);
        return ApiResponse.onSuccess(fanoutJobService.getFanoutJob(jobId));
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
                    responseCode = "409",
                    description = "ADMIN4028: fan-out job 진행 중에는 개별 재생성을 시작할 수 없습니다."
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
