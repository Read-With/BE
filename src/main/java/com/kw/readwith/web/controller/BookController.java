package com.kw.readwith.web.controller;

import com.kw.readwith.apiPayload.ApiResponse;
import com.kw.readwith.dto.book.BookDetailDTO;
import com.kw.readwith.dto.book.BookSummaryDTO;
import com.kw.readwith.service.BookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/books")
public class BookController {

    private final BookService bookService;

    @Operation(summary = "도서 목록 조회 API", description = "키워드, 언어, 즐겨찾기 여부, 정렬 기준에 따라 도서 목록을 조회합니다.")
    @GetMapping
    @Parameters({
            @Parameter(name = "keyword", description = "검색 키워드 (제목 또는 저자)"),
            @Parameter(name = "language", description = "언어 (예: kr, en)"),
            @Parameter(name = "favoriteOnly", description = "즐겨찾기한 도서만 볼지 여부"),
            @Parameter(name = "sortBy", description = "정렬 기준 (예: title, updatedAt)"),
            @Parameter(name = "userId", description = "사용자 ID (비회원인 경우 null)")
    })
    public ApiResponse<List<BookSummaryDTO>> getBooks(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) Boolean favoriteOnly,
            @RequestParam(required = false, defaultValue = "updatedAt") String sortBy,
            @RequestParam(required = false) Long userId) {
        List<BookSummaryDTO> response = bookService.getBooks(keyword, language, favoriteOnly, sortBy, userId);
        return ApiResponse.onSuccess(response);
    }

    @Operation(summary = "도서 상세 정보 조회 API", description = "특정 도서의 상세 정보를 조회합니다.")
    @GetMapping("/{bookId}")
    @Parameters({
            @Parameter(name = "bookId", description = "도서 ID"),
            @Parameter(name = "userId", description = "사용자 ID (비회원인 경우 null)")
    })
    public ApiResponse<BookDetailDTO> getBook(
            @PathVariable Long bookId,
            @RequestParam(required = false) Long userId) {
        BookDetailDTO response = bookService.getBook(bookId, userId);
        return ApiResponse.onSuccess(response);
    }

    @Operation(summary = "도서 업로드 API", description = "EPUB 파일을 업로드하여 새로운 도서를 생성합니다.")
    @PostMapping("/upload")
    public ApiResponse<BookDetailDTO> uploadBook(
            // userId를 파라미터로 받도록 변경
            @RequestParam("userId") Long userId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam("author") String author,
            @RequestParam("language") String language) {

        // 파라미터로 받은 userId를 서비스에 정상적으로 전달
        BookDetailDTO response = bookService.uploadBook(userId, file, title, author, language);
        return ApiResponse.onSuccess(response);
    }
}