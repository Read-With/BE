package com.kw.readwith.web.controller;

import com.kw.readwith.apiPayload.ApiResponse;
import com.kw.readwith.dto.admin.AnalysisInputResponseDTO;
import com.kw.readwith.dto.admin.BookAdminDetailDTO;
import com.kw.readwith.dto.admin.UnsummarizedItemDTO;
import com.kw.readwith.dto.book.BookSummaryDTO;
import com.kw.readwith.dto.admin.CharacterDTO;
import com.kw.readwith.dto.admin.NormalizationJobResponseDTO;
import com.kw.readwith.service.AdminService;
import com.kw.readwith.service.AnalysisInputExportService;
import com.kw.readwith.service.normalization.NormalizationJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    @Operation(summary = "모든 도서 전체 정보 조회", description = "book 테이블의 모든 행들의 모든 칼럼값들을 조회합니다. (관리자용)")
    @GetMapping("/books")
    public ApiResponse<List<BookAdminDetailDTO>> getAllBooks() {
        List<BookAdminDetailDTO> response = adminService.getAllBooks();
        return ApiResponse.onSuccess(response);
    }

    @Operation(summary = "최근 정규화 Job 목록 조회", description = "최근에 진행된 정규화 작업 목록을 최신순으로 조회합니다.")
    @GetMapping("/jobs/normalization/latest")
    public ApiResponse<List<NormalizationJobResponseDTO>> getRecentNormalizationJobs() {
        List<NormalizationJobResponseDTO> response = normalizationJobService.getRecentNormalizationJobs();
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
            description = "`chapter_{n}_relationships_event_{m}.json` 파일들을 업로드합니다. payload 루트는 `chapterIndex`, `eventId`, `items`, `nodeWeights`를 사용합니다."
    )
    @PostMapping(value = "/books/{bookId}/relationships", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<String> uploadRelationships(
            @Parameter(description = "업로드 대상 도서 ID", required = true) @PathVariable Long bookId,
            @Parameter(description = "관계 JSON 파일 목록", required = true) @RequestParam("files") List<MultipartFile> files) {
        adminService.uploadRelationships(bookId, files);
        return ApiResponse.onSuccess("Relationships for the book have been successfully uploaded.");
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
            summary = "캐릭터 이미지 재생성",
            description = "특정 캐릭터의 프로필 이미지를 다시 생성합니다. 기존 이미지가 없거나 생성이 실패했을 때 사용합니다.",
            security = {}
    )
    @PostMapping(
            value = "/characters/{characterId}/regenerate-image",
            consumes = MediaType.ALL_VALUE
    )
    public ApiResponse<String> regenerateCharacterImage(
            @Parameter(description = "이미지를 다시 생성할 캐릭터 DB ID", required = true) @PathVariable Long characterId) {
        adminService.regenerateCharacterImage(characterId);
        return ApiResponse.onSuccess("Character image regeneration has been started.");
    }
}
