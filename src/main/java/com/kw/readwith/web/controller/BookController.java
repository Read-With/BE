package com.kw.readwith.web.controller;

import com.kw.readwith.apiPayload.ApiResponse;
import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.dto.book.BookDetailDTO;
import com.kw.readwith.dto.book.BookSummaryDTO;
import com.kw.readwith.service.BookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
@Tag(name = "Books", description = "도서 관련 API")
public class BookController {

    private final BookService bookService;

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Long) {
            return (Long) authentication.getPrincipal();
        }
        throw new GeneralException(ErrorStatus._UNAUTHORIZED);
    }

    // 도서 목록 조회
    @GetMapping
    @Operation(summary = "도서 목록 조회", description = "검색/필터/정렬 파라미터를 이용해 도서 목록을 반환합니다.")
    public ApiResponse<List<BookSummaryDTO>> getBooks(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String language,
            @RequestParam(defaultValue = "updatedAt") String sort,
            @RequestParam(required = false) Boolean favorite
    ) {
        Long userId = getCurrentUserId();
        List<BookSummaryDTO> response = bookService.getBooks(q, language, favorite, sort, userId);
        return ApiResponse.onSuccess(response);
    }

    // 단일 도서 조회
    @GetMapping("/{bookId}")
    @Operation(summary = "단일 도서 조회", description = "도서 ID로 단일 도서를 조회합니다.")
    public ApiResponse<BookDetailDTO> getBook(@PathVariable Long bookId) {
        Long userId = getCurrentUserId();
        BookDetailDTO response = bookService.getBook(bookId, userId);
        return ApiResponse.onSuccess(response);
    }

    // 도서 업로드
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "도서 업로드", description = "EPUB 파일을 업로드하여 도서를 등록합니다.")
    public ApiResponse<BookDetailDTO> uploadBook(@RequestPart("file") MultipartFile file,
                                                    @RequestPart(value = "title", required = false) String title,
                                                    @RequestPart(value = "author", required = false) String author,
                                                    @RequestPart(value = "language", required = false) String language) {
        Long userId = getCurrentUserId();
        BookDetailDTO response = bookService.uploadBook(userId, file, title, author, language);
        return ApiResponse.onSuccess(response);
    }
}
