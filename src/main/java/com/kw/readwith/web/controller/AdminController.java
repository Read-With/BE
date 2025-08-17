package com.kw.readwith.web.controller;

import com.kw.readwith.apiPayload.ApiResponse;
import com.kw.readwith.dto.admin.UnsummarizedItemDTO;
import com.kw.readwith.dto.book.BookSummaryDTO;
import com.kw.readwith.service.AdminService;
import com.kw.readwith.service.BookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final BookService bookService; // BookService 의존성 추가

    @Operation(summary = "미요약 도서 목록 조회 API", description = "전체 책 중에서 요약이 완료되지 않은 책들의 목록을 조회합니다.")
    @GetMapping("/books/unsummarized")
    public ApiResponse<List<BookSummaryDTO>> getUnsummarizedBooks() {
        List<BookSummaryDTO> response = bookService.getUnsummarizedBooks();
        return ApiResponse.onSuccess(response);
    }

    @Operation(summary = "미요약 챕터 목록 조회 API", description = "전체 챕터 중에서 요약이 필요한 챕터들의 목록을 조회합니다.")
    @GetMapping("/chapters/unsummarized")
    public ApiResponse<List<UnsummarizedItemDTO>> getUnsummarizedChapters() {
        List<UnsummarizedItemDTO> response = bookService.getUnsummarizedChapters();
        return ApiResponse.onSuccess(response);
    }

    @Operation(summary = "챕터 요약본 업로드 API", description = "특정 챕터에 대한 인물별 요약 정보가 담긴 JSON 파일을 업로드합니다.")
    @PostMapping(value = "/books/{bookId}/chapters/{idx}/summary", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<String> uploadChapterSummary(
            @Parameter(description = "요약본을 추가할 책의 ID") @PathVariable Long bookId,
            @Parameter(description = "요약본을 추가할 챕터의 순서(index)") @PathVariable Integer idx,
            @Parameter(description = "인물별 요약 정보가 담긴 JSON 파일") @RequestParam("file") MultipartFile file) {

        bookService.uploadChapterSummary(bookId, idx, file);
        return ApiResponse.onSuccess("Chapter summary has been successfully uploaded.");
    }

    @Operation(summary = "인물 정보 업로드 API", description = "특정 책에 대한 인물 정보가 담긴 JSON 파일을 업로드합니다.")
    @PostMapping(value = "/books/{bookId}/characters", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<String> uploadCharacters(
            @Parameter(description = "인물 정보를 추가할 책의 ID") @PathVariable Long bookId,
            @Parameter(description = "인물 정보가 담긴 JSON 파일") @RequestParam("file") MultipartFile file) {

        adminService.uploadCharacters(bookId, file);
        return ApiResponse.onSuccess("Characters have been successfully uploaded.");
    }

    @Operation(summary = "이벤트 정보 업로드 API", description = "책의 특정 챕터에 대한 이벤트 정보(JSON)를 업로드합니다.")
    @PostMapping(value = "/books/{bookId}/chapters/{chapterIdx}/events", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<String> uploadEvents(
            @Parameter(description = "이벤트 정보를 추가할 책의 ID") @PathVariable(name = "bookId") Long bookId,
            @Parameter(description = "이벤트 정보를 추가할 챕터의 순서(index)") @PathVariable(name = "chapterIdx") Integer chapterIdx,
            @Parameter(description = "이벤트 정보가 담긴 JSON 파일") @RequestParam("file") MultipartFile file) {
        adminService.uploadEvents(bookId, chapterIdx, file);
        return ApiResponse.onSuccess("Events uploaded successfully.");
    }
}