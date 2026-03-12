package com.kw.readwith.web.controller;

import com.kw.readwith.apiPayload.ApiResponse;
import com.kw.readwith.dto.admin.UnsummarizedItemDTO;
import com.kw.readwith.dto.book.BookSummaryDTO;
import com.kw.readwith.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/admin", "/api/v2/admin"})
public class AdminController {

    private final AdminService adminService;

    @Operation(summary = "인물 정보 업로드 API", description = "특정 책에 대한 인물 정보가 담긴 JSON 파일을 업로드합니다.")
    @PostMapping(value = "/books/{bookId}/characters", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<String> uploadCharacters(
            @Parameter(description = "인물 정보를 추가할 책의 ID") @PathVariable Long bookId,
            @Parameter(description = "인물 정보가 담긴 JSON 파일") @RequestParam("file") MultipartFile file) {
        adminService.uploadCharacters(bookId, file);
        return ApiResponse.onSuccess("Characters have been successfully uploaded.");
    }

    @Operation(summary = "인물 정보 삭제 API", description = "특정 책에 대한 모든 인물 정보를 삭제합니다.")
    @DeleteMapping("/books/{bookId}/characters")
    public ApiResponse<String> deleteCharacters(
            @Parameter(description = "인물 정보를 삭제할 책의 ID") @PathVariable Long bookId) {
        adminService.deleteCharacters(bookId);
        return ApiResponse.onSuccess("Characters for the book have been successfully deleted.");
    }

    @Operation(summary = "이벤트 정보 다중 업로드 API", description = "특정 책에 대한 챕터별 이벤트 정보가 담긴 JSON 파일들을 업로드합니다.")
    @PostMapping(value = "/books/{bookId}/events", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<String> uploadEvents(
            @Parameter(description = "이벤트를 추가할 책의 ID") @PathVariable Long bookId,
            @Parameter(description = "챕터별 이벤트 정보 파일 목록 (파일명: chapter<번호>_events.json)") @RequestParam("files") List<MultipartFile> files) {
        adminService.uploadEvents(bookId, files);
        return ApiResponse.onSuccess("Events have been successfully uploaded.");
    }

    @Operation(summary = "이벤트 정보 삭제 API", description = "특정 챕터에 대한 모든 이벤트 정보를 삭제합니다.")
    @DeleteMapping("/books/{bookId}/chapters/{chapterIdx}/events")
    public ApiResponse<String> deleteEvents(
            @Parameter(description = "이벤트 정보를 삭제할 책의 ID") @PathVariable Long bookId,
            @Parameter(description = "이벤트 정보를 삭제할 챕터의 순서(index)") @PathVariable Integer chapterIdx) {
        adminService.deleteEvents(bookId, chapterIdx);
        return ApiResponse.onSuccess("Events for the chapter have been successfully deleted.");
    }

    @Operation(summary = "관계 정보 다중 업로드 API", description = "특정 책에 대한 관계 정보(JSON) 파일들을 업로드합니다. 파일명 규칙: chapter<번호>_relationships_<언어>_event_<번호>.json")
    @PostMapping(value = "/books/{bookId}/relationships", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<String> uploadRelationships(
            @Parameter(description = "관계 정보를 추가할 책의 ID") @PathVariable Long bookId,
            @Parameter(description = "관계 정보가 담긴 JSON 파일 목록") @RequestParam("files") List<MultipartFile> files) {
        adminService.uploadRelationships(bookId, files);
        return ApiResponse.onSuccess("Relationships for the book have been successfully uploaded.");
    }

    @Operation(summary = "관계 정보 삭제 API", description = "특정 이벤트에 대한 모든 관계 정보(엣지)를 삭제합니다.")
    @DeleteMapping("/books/{bookId}/chapters/{chapterIdx}/events/{eventIdx}/relationships")
    public ApiResponse<String> deleteRelationships(
            @Parameter(description = "관계 정보를 삭제할 책의 ID") @PathVariable Long bookId,
            @Parameter(description = "관계 정보를 삭제할 챕터의 순서(index)") @PathVariable Integer chapterIdx,
            @Parameter(description = "관계 정보를 삭제할 이벤트의 순서(index)") @PathVariable Integer eventIdx) {
        adminService.deleteRelationships(bookId, chapterIdx, eventIdx);
        return ApiResponse.onSuccess("Relationships have been successfully deleted.");
    }

    @Operation(summary = "챕터 요약본 다중 업로드 API", description = "특정 책에 대한 챕터별 요약 정보가 담긴 JSON 파일들을 업로드합니다.")
    @PostMapping(value = "/books/{bookId}/summary", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<String> uploadChapterSummaries(
            @Parameter(description = "요약본을 추가할 책의 ID") @PathVariable Long bookId,
            @Parameter(description = "인물별 요약 정보 파일 목록 (파일명: chapter<번호>_perspective_summaries.json)") @RequestParam("files") List<MultipartFile> files) {
        adminService.uploadChapterSummaries(bookId, files);
        return ApiResponse.onSuccess("Chapter summaries have been successfully uploaded.");
    }

    @Operation(summary = "챕터 요약본 삭제 API", description = "특정 챕터에 대한 요약본 정보를 삭제합니다.")
    @DeleteMapping("/books/{bookId}/chapters/{chapterIdx}/summary")
    public ApiResponse<String> deleteChapterSummary(
            @Parameter(description = "요약본을 삭제할 책의 ID") @PathVariable Long bookId,
            @Parameter(description = "요약본을 삭제할 챕터의 순서(index)") @PathVariable Integer chapterIdx) {
        adminService.deleteChapterSummary(bookId, chapterIdx);
        return ApiResponse.onSuccess("Chapter summary has been successfully deleted.");
    }

    @Operation(summary = "미요약 도서 목록 조회 API", description = "전체 책 중에서 요약이 완료되지 않은 책들의 목록을 조회합니다.")
    @GetMapping("/books/unsummarized")
    public ApiResponse<List<BookSummaryDTO>> getUnsummarizedBooks() {
        List<BookSummaryDTO> response = adminService.getUnsummarizedBooks();
        return ApiResponse.onSuccess(response);
    }

    @Operation(summary = "미요약 챕터 목록 조회 API", description = "전체 챕터 중에서 요약이 필요한 챕터들의 목록을 조회합니다.")
    @GetMapping("/chapters/unsummarized")
    public ApiResponse<List<UnsummarizedItemDTO>> getUnsummarizedChapters() {
        List<UnsummarizedItemDTO> response = adminService.getUnsummarizedChapters();
        return ApiResponse.onSuccess(response);
    }

    @Operation(
        summary = "캐릭터 이미지 재생성 API", 
        description = "특정 캐릭터의 프로필 이미지를 재생성합니다. 이미지 생성에 실패했거나 품질이 좋지 않은 경우 사용합니다.",
        security = {}
    )
    @PostMapping(
        value = "/characters/{characterId}/regenerate-image",
        consumes = MediaType.ALL_VALUE  // body 없는 POST 요청 허용
    )
    public ApiResponse<String> regenerateCharacterImage(
            @Parameter(description = "이미지를 재생성할 캐릭터의 ID") @PathVariable Long characterId) {
        adminService.regenerateCharacterImage(characterId);
        return ApiResponse.onSuccess("Character image regeneration has been started.");
    }
}
