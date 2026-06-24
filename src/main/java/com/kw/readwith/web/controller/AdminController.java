package com.kw.readwith.web.controller;

import com.kw.readwith.apiPayload.ApiResponse;
import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.dto.admin.AnalysisInputResponseDTO;
import com.kw.readwith.dto.admin.BookAdminDetailDTO;
import com.kw.readwith.dto.admin.CharacterImageAssetDTO;
import com.kw.readwith.dto.admin.CharacterImageBookStatusResponseDTO;
import com.kw.readwith.dto.admin.CharacterImageCandidateRequestDTO;
import com.kw.readwith.dto.admin.CharacterDTO;
import com.kw.readwith.dto.admin.CharacterImageFanoutRequestDTO;
import com.kw.readwith.dto.admin.CharacterImageFanoutResponseDTO;
import com.kw.readwith.dto.admin.CharacterImageReferenceCandidateRequestDTO;
import com.kw.readwith.dto.admin.NormalizationJobResponseDTO;
import com.kw.readwith.dto.admin.ProcessingJobLogResponseDTO;
import com.kw.readwith.dto.admin.UnsummarizedItemDTO;
import com.kw.readwith.dto.book.BookSummaryDTO;
import com.kw.readwith.service.AdminService;
import com.kw.readwith.service.AnalysisInputExportService;
import com.kw.readwith.service.CharacterImageAdminService;
import com.kw.readwith.service.normalization.NormalizationJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/admin", "/api/v2/admin"})
@Tag(name = "관리자 업로드", description = "AI/관리자 산출물 적재와 운영 보조 기능을 제공하는 API입니다.")
public class AdminController {

    private final AdminService adminService;
    private final AnalysisInputExportService analysisInputExportService;
    private final NormalizationJobService normalizationJobService;
    private final CharacterImageAdminService characterImageAdminService;

    @Operation(summary = "모든 도서 전체 정보 조회", description = "book 테이블의 모든 행들의 모든 칼럼값들을 조회합니다. (관리자용)")
    @GetMapping("/books")
    public ApiResponse<List<BookAdminDetailDTO>> getAllBooks() {
        List<BookAdminDetailDTO> response = adminService.getAllBooks();
        return ApiResponse.onSuccess(response);
    }

    @Operation(summary = "최근 정규화 Job 목록 조회", description = "최근에 진행된 정규화 작업 목록을 최신순으로 조회합니다.")
    @GetMapping("/normalization/jobs/latest")
    public ApiResponse<List<NormalizationJobResponseDTO>> getRecentNormalizationJobs() {
        List<NormalizationJobResponseDTO> response = normalizationJobService.getRecentNormalizationJobs();
        return ApiResponse.onSuccess(response);
    }

    @Operation(summary = "최근 Job Log 목록 조회", description = "시스템 전체에서 기록된 최신 로그 목록을 최신순으로 조회합니다.")
    @GetMapping("/jobs/logs/latest")
    public ApiResponse<List<ProcessingJobLogResponseDTO>> getRecentJobLogs() {
        List<ProcessingJobLogResponseDTO> response = normalizationJobService.getRecentJobLogs();
        return ApiResponse.onSuccess(response);
    }

    @Operation(
            summary = "인물 업로드",
            description = "`book_characters.json` 파일을 업로드합니다. payload 루트는 `items`이며, 각 인물은 책 내부 `characterId`를 가져야 합니다."
    )
    @PostMapping(value = "/books/{bookId}/characters", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<String> uploadCharacters(
            @Parameter(description = "업로드 대상 도서 ID", required = true) @PathVariable Long bookId,
            @Parameter(description = "인물 JSON 파일", required = true) @RequestParam("file") MultipartFile file) {
        adminService.uploadCharacters(bookId, file);
        return ApiResponse.onSuccess("Characters have been successfully uploaded.");
    }

    @Operation(summary = "도서별 인물 목록 조회", description = "특정 도서에 속한 인물들의 기본 정보와 이미지 생성 상태를 조회합니다.")
    @GetMapping("/books/{bookId}/characters")
    public ApiResponse<List<CharacterDTO>> getCharactersByBookId(
            @Parameter(description = "조회 대상 도서 ID", required = true) @PathVariable Long bookId) {
        List<CharacterDTO> response = adminService.getCharactersByBookId(bookId);
        return ApiResponse.onSuccess(response);
    }

    @Operation(summary = "인물 전체 삭제", description = "특정 도서에 적재된 인물 정보를 모두 삭제합니다.")
    @DeleteMapping("/books/{bookId}/characters")
    public ApiResponse<String> deleteCharacters(
            @Parameter(description = "삭제 대상 도서 ID", required = true) @PathVariable Long bookId) {
        adminService.deleteCharacters(bookId);
        return ApiResponse.onSuccess("Characters for the book have been successfully deleted.");
    }

    @Operation(
            summary = "이벤트 업로드",
            description = "`chapter_{n}_events.json` 파일들을 업로드합니다. `chapterIndex`, `items`, `eventId`, `startTxtOffset`, `endTxtOffset`, `eventText` 구조를 사용합니다."
    )
    @PostMapping(value = "/books/{bookId}/events", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<String> uploadEvents(
            @Parameter(description = "업로드 대상 도서 ID", required = true) @PathVariable Long bookId,
            @Parameter(description = "이벤트 JSON 파일 목록", required = true) @RequestParam("files") List<MultipartFile> files) {
        adminService.uploadEvents(bookId, files);
        return ApiResponse.onSuccess("Events have been successfully uploaded.");
    }

    @Operation(summary = "챕터 이벤트 전체 삭제", description = "특정 챕터에 적재된 이벤트를 모두 삭제합니다.")
    @DeleteMapping("/books/{bookId}/chapters/{chapterIdx}/events")
    public ApiResponse<String> deleteEvents(
            @Parameter(description = "삭제 대상 도서 ID", required = true) @PathVariable Long bookId,
            @Parameter(description = "삭제할 챕터 인덱스(1-based)", required = true) @PathVariable Integer chapterIdx) {
        adminService.deleteEvents(bookId, chapterIdx);
        return ApiResponse.onSuccess("Events for the chapter have been successfully deleted.");
    }

    @Operation(
            summary = "관계 업로드",
            description = "비활성화된 legacy endpoint입니다. `/books/{bookId}/relationship-deltas`를 사용합니다."
    )
    @PostMapping(value = "/books/{bookId}/relationships", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<String> uploadRelationships(
            @Parameter(description = "업로드 대상 도서 ID", required = true) @PathVariable Long bookId,
            @Parameter(description = "관계 JSON 파일 목록", required = true) @RequestParam("files") List<MultipartFile> files) {
        throw new GeneralException(ErrorStatus.RELATIONSHIP_LEGACY_API_DISABLED);
    }

    @Operation(
            summary = "관계 delta 업로드",
            description = "`relationship-delta-v1` 파일들을 event 단위 replace 방식으로 업로드합니다."
    )
    @PostMapping(value = "/books/{bookId}/relationship-deltas", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<String> uploadRelationshipDeltas(
            @Parameter(description = "업로드 대상 도서 ID", required = true) @PathVariable Long bookId,
            @Parameter(description = "관계 delta JSON 파일 목록", required = true) @RequestParam("files") List<MultipartFile> files) {
        adminService.uploadRelationshipDeltas(bookId, files);
        return ApiResponse.onSuccess("Relationship deltas for the book have been successfully uploaded.");
    }

    @Operation(summary = "이벤트 관계 삭제", description = "특정 이벤트에 적재된 관계 edge와 node weight를 함께 삭제합니다.")
    @DeleteMapping("/books/{bookId}/chapters/{chapterIdx}/events/{eventIdx}/relationships")
    public ApiResponse<String> deleteRelationships(
            @Parameter(description = "삭제 대상 도서 ID", required = true) @PathVariable Long bookId,
            @Parameter(description = "이벤트가 속한 챕터 인덱스(1-based)", required = true) @PathVariable Integer chapterIdx,
            @Parameter(description = "삭제할 이벤트 인덱스", required = true) @PathVariable Integer eventIdx) {
        adminService.deleteRelationships(bookId, chapterIdx, eventIdx);
        return ApiResponse.onSuccess("Relationships have been successfully deleted.");
    }

    @Operation(
            summary = "챕터 요약 업로드",
            description = "`chapter_{n}_summaries_Ko.json` 파일들을 업로드합니다. 현재는 `language=ko` 요약만 공식 입력으로 받습니다."
    )
    @PostMapping(value = "/books/{bookId}/summary", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<String> uploadChapterSummaries(
            @Parameter(description = "업로드 대상 도서 ID", required = true) @PathVariable Long bookId,
            @Parameter(description = "챕터 요약 JSON 파일 목록", required = true) @RequestParam("files") List<MultipartFile> files) {
        adminService.uploadChapterSummaries(bookId, files);
        return ApiResponse.onSuccess("Chapter summaries have been successfully uploaded.");
    }

    @Operation(
            summary = "AI 분석 입력 산출물 조회",
            description = "정규화가 완료된 도서의 `meta.json`과 챕터별 `chapter_*.txt`를 presigned URL로 내려줍니다. AI 서버는 이 응답을 받아 만료 시간 안에 private S3 산출물을 다운로드하면 됩니다."
    )
    @GetMapping("/books/{bookId}/analysis-input")
    public ApiResponse<AnalysisInputResponseDTO> getAnalysisInput(
            @Parameter(description = "정규화 산출물을 조회할 도서 ID", required = true) @PathVariable Long bookId) {
        AnalysisInputResponseDTO response = analysisInputExportService.getAnalysisInput(bookId);
        return ApiResponse.onSuccess(response);
    }

    @Operation(summary = "챕터 요약 삭제", description = "특정 챕터에 적재된 POV summary를 삭제합니다.")
    @DeleteMapping("/books/{bookId}/chapters/{chapterIdx}/summary")
    public ApiResponse<String> deleteChapterSummary(
            @Parameter(description = "삭제 대상 도서 ID", required = true) @PathVariable Long bookId,
            @Parameter(description = "삭제할 챕터 인덱스(1-based)", required = true) @PathVariable Integer chapterIdx) {
        adminService.deleteChapterSummary(bookId, chapterIdx);
        return ApiResponse.onSuccess("Chapter summary has been successfully deleted.");
    }

    @Operation(summary = "미요약 도서 목록 조회", description = "legacy summary 플래그 기준으로 아직 요약이 완료되지 않은 도서를 조회합니다.")
    @GetMapping("/books/unsummarized")
    public ApiResponse<List<BookSummaryDTO>> getUnsummarizedBooks() {
        List<BookSummaryDTO> response = adminService.getUnsummarizedBooks();
        return ApiResponse.onSuccess(response);
    }

    @Operation(summary = "미요약 챕터 목록 조회", description = "POV summary가 아직 캐시되지 않은 챕터 목록을 조회합니다.")
    @GetMapping("/chapters/unsummarized")
    public ApiResponse<List<UnsummarizedItemDTO>> getUnsummarizedChapters() {
        List<UnsummarizedItemDTO> response = adminService.getUnsummarizedChapters();
        return ApiResponse.onSuccess(response);
    }

    @Operation(
            summary = "도서 캐릭터 이미지 QA Gate 상태 조회",
            description = """
                    도서 단위 대표 이미지와 캐릭터별 최신 이미지 후보 상태를 조회합니다.
                    대표 이미지 상태: NONE, CANDIDATE_GENERATING, QA_FAILED, QA_PASSED, APPROVED, REJECTED, SUPERSEDED.
                    이미지 asset 상태: GENERATING, QA_PENDING, QA_PASSED, QA_FAILED, REVIEW_REQUIRED, APPROVED, REJECTED, PUBLISHED, FAILED, STALE_REFERENCE, STALE_PROMPT, SUPERSEDED.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "이미지 QA Gate 상태 조회 성공",
                    content = @Content(schema = @Schema(implementation = CharacterImageBookStatusResponseDTO.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "PROGRESS4001: 해당 책을 찾을 수 없습니다."
            )
    })
    @GetMapping("/books/{bookId}/character-images")
    public ApiResponse<CharacterImageBookStatusResponseDTO> getCharacterImageStatus(
            @Parameter(description = "조회 대상 도서 ID", required = true, example = "1") @PathVariable Long bookId) {
        CharacterImageBookStatusResponseDTO response = characterImageAdminService.getBookStatus(bookId);
        return ApiResponse.onSuccess(response);
    }

    @Operation(
            summary = "대표 이미지 후보 생성",
            description = """
                    도서의 대표 스타일 기준이 될 이미지 후보를 생성합니다.
                    요청에서 characterId를 생략하면 주요 인물 우선, 이름순으로 대표 인물을 자동 선택합니다.
                    생성 직후 autoQa=true이면 QA를 실행하고, QA_PASSED가 되더라도 이 API는 게시하지 않습니다.
                    대표 후보는 approve API를 통과해야 이후 fan-out 생성의 reference image가 됩니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "대표 이미지 후보 생성 요청 처리 성공. OpenAI/S3 실패는 asset.status=FAILED와 failureCode로 반환됩니다.",
                    content = @Content(schema = @Schema(implementation = CharacterImageAssetDTO.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "ADMIN4006: 요청한 인물이 해당 책 소속이 아닙니다. ADMIN4026: 대표 후보로 사용할 인물을 찾을 수 없습니다."
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "PROGRESS4001: 해당 책을 찾을 수 없습니다. ADMIN4004: 해당 인물을 찾을 수 없습니다."
            )
    })
    @PostMapping("/books/{bookId}/character-images/reference-candidates")
    public ApiResponse<CharacterImageAssetDTO> createReferenceImageCandidate(
            @Parameter(description = "대표 이미지 후보를 생성할 도서 ID", required = true, example = "1") @PathVariable Long bookId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "대표 이미지 후보 생성 옵션. 생략 가능하며, characterId를 생략하면 대표 인물을 자동 선택합니다.",
                    required = false,
                    content = @Content(schema = @Schema(implementation = CharacterImageReferenceCandidateRequestDTO.class))
            )
            @RequestBody(required = false) CharacterImageReferenceCandidateRequestDTO request) {
        CharacterImageAssetDTO response = characterImageAdminService.createReferenceCandidate(bookId, request);
        return ApiResponse.onSuccess(response);
    }

    @Operation(
            summary = "대표 이미지 후보 승인",
            description = """
                    QA_PASSED 상태의 대표 이미지 후보를 승인합니다.
                    승인되면 해당 asset이 도서의 active reference image가 되고, 기존 reference로 생성된 게시/승인 후보는 STALE_REFERENCE 대상이 됩니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "대표 이미지 후보 승인 성공",
                    content = @Content(schema = @Schema(implementation = CharacterImageAssetDTO.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "ADMIN4021: 요청한 책의 이미지 후보가 아닙니다. ADMIN4022: REFERENCE_SEED가 아니거나 QA_PASSED/APPROVED/PUBLISHED 상태가 아닙니다."
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "PROGRESS4001: 해당 책을 찾을 수 없습니다."
            )
    })
    @PostMapping("/books/{bookId}/character-images/reference-candidates/{assetId}/approve")
    public ApiResponse<CharacterImageAssetDTO> approveReferenceImageCandidate(
            @Parameter(description = "도서 ID", required = true, example = "1") @PathVariable Long bookId,
            @Parameter(description = "승인할 대표 이미지 asset ID", required = true, example = "100") @PathVariable Long assetId) {
        CharacterImageAssetDTO response = characterImageAdminService.approveReferenceCandidate(bookId, assetId);
        return ApiResponse.onSuccess(response);
    }

    @Operation(
            summary = "대표 이미지 후보 거절",
            description = "대표 이미지 후보를 REJECTED 상태로 변경합니다. 현재 active reference인 후보를 거절하면 도서 reference 상태도 REJECTED로 변경됩니다."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "대표 이미지 후보 거절 성공",
                    content = @Content(schema = @Schema(implementation = CharacterImageAssetDTO.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "ADMIN4021: 요청한 책의 이미지 후보가 아닙니다. ADMIN4022: REFERENCE_SEED 역할이 아닙니다."
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "PROGRESS4001: 해당 책을 찾을 수 없습니다."
            )
    })
    @PostMapping("/books/{bookId}/character-images/reference-candidates/{assetId}/reject")
    public ApiResponse<CharacterImageAssetDTO> rejectReferenceImageCandidate(
            @Parameter(description = "도서 ID", required = true, example = "1") @PathVariable Long bookId,
            @Parameter(description = "거절할 대표 이미지 asset ID", required = true, example = "100") @PathVariable Long assetId) {
        CharacterImageAssetDTO response = characterImageAdminService.rejectReferenceCandidate(bookId, assetId);
        return ApiResponse.onSuccess(response);
    }

    @Operation(
            summary = "대표 이미지 기반 캐릭터 이미지 fan-out 생성",
            description = """
                    승인된 대표 이미지(reference image)를 입력 이미지로 사용해 대상 캐릭터들의 이미지 후보를 생성합니다.
                    scope 허용값: MAIN_ONLY, GRAPH_VISIBLE, SELECTED, STALE_ONLY, FAILED_ONLY, ALL.
                    publishPolicy 허용값: AUTO_AFTER_QA, MANUAL.
                    MANUAL은 후보만 만들고, AUTO_AFTER_QA는 QA_PASSED 후보를 즉시 게시합니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "fan-out 생성 처리 성공. 개별 실패는 asset.status=FAILED와 failureCode로 반환됩니다.",
                    content = @Content(schema = @Schema(implementation = CharacterImageFanoutResponseDTO.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "ADMIN4023: 승인된 대표 이미지가 없습니다. ADMIN4024: 잘못된 scope 값입니다. ADMIN4025: 잘못된 publishPolicy 값입니다. ADMIN4006: 선택 인물이 책 소속이 아닙니다."
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "PROGRESS4001: 해당 책을 찾을 수 없습니다. ADMIN4004: 선택 인물을 찾을 수 없습니다."
            )
    })
    @PostMapping(value = "/books/{bookId}/character-images/fanout", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<CharacterImageFanoutResponseDTO> fanoutCharacterImages(
            @Parameter(description = "fan-out을 실행할 도서 ID", required = true, example = "1") @PathVariable Long bookId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "fan-out 생성 범위와 게시 정책",
                    required = true,
                    content = @Content(schema = @Schema(implementation = CharacterImageFanoutRequestDTO.class))
            )
            @RequestBody CharacterImageFanoutRequestDTO request) {
        CharacterImageFanoutResponseDTO response = characterImageAdminService.fanout(bookId, request);
        return ApiResponse.onSuccess(response);
    }

    @Operation(
            summary = "캐릭터 이미지 후보 생성",
            description = """
                    특정 캐릭터의 이미지 후보를 생성합니다.
                    도서에 승인된 대표 이미지가 있으면 reference edit 방식으로 생성하고, 없거나 대표 캐릭터 본인이면 text-to-image 방식으로 생성합니다.
                    기본 publishPolicy는 MANUAL이라 QA 통과 후에도 바로 게시하지 않습니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "캐릭터 이미지 후보 생성 처리 성공. OpenAI/S3 실패는 asset.status=FAILED와 failureCode로 반환됩니다.",
                    content = @Content(schema = @Schema(implementation = CharacterImageAssetDTO.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "ADMIN4025: 잘못된 publishPolicy 값입니다."
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "ADMIN4004: 해당 인물을 찾을 수 없습니다."
            )
    })
    @PostMapping("/characters/{characterId}/image-candidates")
    public ApiResponse<CharacterImageAssetDTO> createCharacterImageCandidate(
            @Parameter(description = "이미지 후보를 생성할 캐릭터 DB ID", required = true, example = "10") @PathVariable Long characterId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "캐릭터 이미지 후보 생성 옵션. 생략 가능하며 기본값은 autoQa=true, publishPolicy=MANUAL입니다.",
                    required = false,
                    content = @Content(schema = @Schema(implementation = CharacterImageCandidateRequestDTO.class))
            )
            @RequestBody(required = false) CharacterImageCandidateRequestDTO request) {
        CharacterImageAssetDTO response = characterImageAdminService.createCharacterCandidate(characterId, request);
        return ApiResponse.onSuccess(response);
    }

    @Operation(
            summary = "캐릭터 이미지 후보 승인/게시",
            description = "QA_PASSED, APPROVED, PUBLISHED 상태의 캐릭터 이미지 후보를 게시 이미지로 반영합니다."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "캐릭터 이미지 후보 승인/게시 성공",
                    content = @Content(schema = @Schema(implementation = CharacterImageAssetDTO.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "ADMIN4021: 요청한 캐릭터의 이미지 후보가 아닙니다. ADMIN4022: 게시 가능한 상태가 아닙니다."
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "ADMIN4004: 해당 인물을 찾을 수 없습니다. ADMIN4020: 해당 이미지 후보를 찾을 수 없습니다."
            )
    })
    @PostMapping("/characters/{characterId}/image-candidates/{assetId}/approve")
    public ApiResponse<CharacterImageAssetDTO> approveCharacterImageCandidate(
            @Parameter(description = "캐릭터 DB ID", required = true, example = "10") @PathVariable Long characterId,
            @Parameter(description = "승인/게시할 이미지 asset ID", required = true, example = "120") @PathVariable Long assetId) {
        CharacterImageAssetDTO response = characterImageAdminService.approveCharacterCandidate(characterId, assetId);
        return ApiResponse.onSuccess(response);
    }

    @Operation(
            summary = "캐릭터 이미지 후보 거절",
            description = "캐릭터 이미지 후보를 REJECTED 상태로 변경합니다."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "캐릭터 이미지 후보 거절 성공",
                    content = @Content(schema = @Schema(implementation = CharacterImageAssetDTO.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "ADMIN4021: 요청한 캐릭터의 이미지 후보가 아닙니다."
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "ADMIN4004: 해당 인물을 찾을 수 없습니다. ADMIN4020: 해당 이미지 후보를 찾을 수 없습니다."
            )
    })
    @PostMapping("/characters/{characterId}/image-candidates/{assetId}/reject")
    public ApiResponse<CharacterImageAssetDTO> rejectCharacterImageCandidate(
            @Parameter(description = "캐릭터 DB ID", required = true, example = "10") @PathVariable Long characterId,
            @Parameter(description = "거절할 이미지 asset ID", required = true, example = "120") @PathVariable Long assetId) {
        CharacterImageAssetDTO response = characterImageAdminService.rejectCharacterCandidate(characterId, assetId);
        return ApiResponse.onSuccess(response);
    }

    @Operation(
            summary = "캐릭터 이미지 재생성",
            description = "호환용 legacy endpoint입니다. 이제 이미지를 바로 게시하지 않고 `/characters/{characterId}/image-candidates`와 같은 후보 생성 흐름으로 처리합니다.",
            security = {}
    )
    @PostMapping(
            value = "/characters/{characterId}/regenerate-image",
            consumes = MediaType.ALL_VALUE
    )
    public ApiResponse<String> regenerateCharacterImage(
            @Parameter(description = "이미지를 다시 생성할 캐릭터 DB ID", required = true) @PathVariable Long characterId) {
        characterImageAdminService.createCharacterCandidate(
                characterId,
                CharacterImageCandidateRequestDTO.builder()
                        .autoQa(true)
                        .publishPolicy("MANUAL")
                        .build()
        );
        return ApiResponse.onSuccess("Character image candidate generation has been started.");
    }
}
