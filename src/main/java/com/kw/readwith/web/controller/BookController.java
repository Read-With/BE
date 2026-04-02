package com.kw.readwith.web.controller;

import com.kw.readwith.apiPayload.ApiResponse;
import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.dto.book.BookDetailDTO;
import com.kw.readwith.dto.book.BookSummaryDTO;
import com.kw.readwith.service.BookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping({"/api/books", "/api/v2/books"})
@RequiredArgsConstructor
@Tag(name = "도서", description = "도서 목록 조회, 상세 조회, EPUB 업로드 API입니다.")
public class BookController {

    private final BookService bookService;

    private Long getCurrentUserIdOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }
        if (authentication.getPrincipal() instanceof Long principal) {
            return principal;
        }
        if ("anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }
        throw new GeneralException(ErrorStatus._UNAUTHORIZED);
    }

    private Long requireCurrentUserId() {
        Long userId = getCurrentUserIdOrNull();
        if (userId != null) {
            return userId;
        }
        throw new GeneralException(ErrorStatus._UNAUTHORIZED);
    }

    @GetMapping
    @Operation(
            summary = "도서 목록 조회",
            description = "현재 노출 가능한 도서 목록을 조회합니다. 정규화가 완료된 도서만 반환합니다."
    )
    public ApiResponse<List<BookSummaryDTO>> getBooks(
            @Parameter(description = "도서 제목 또는 저자 검색어", example = "셜록")
            @RequestParam(required = false) String q,
            @Parameter(description = "언어 코드 필터", example = "ko")
            @RequestParam(required = false) String language,
            @Parameter(description = "정렬 기준. `updatedAt` 또는 `title`을 사용합니다.", example = "updatedAt")
            @RequestParam(defaultValue = "updatedAt") String sort,
            @Parameter(description = "true이면 즐겨찾기한 도서만 반환합니다.", example = "false")
            @RequestParam(required = false) Boolean favorite
    ) {
        Long userId = getCurrentUserIdOrNull();
        List<BookSummaryDTO> response = bookService.getBooks(q, language, favorite, sort, userId);
        return ApiResponse.onSuccess(response);
    }

    @GetMapping("/{bookId}")
    @Operation(
            summary = "도서 상세 조회",
            description = "도서 상세 메타데이터를 조회합니다. 정규화가 완료된 도서만 조회할 수 있습니다."
    )
    public ApiResponse<BookDetailDTO> getBook(
            @Parameter(description = "조회할 도서 ID", required = true, example = "42")
            @PathVariable Long bookId) {
        Long userId = getCurrentUserIdOrNull();
        BookDetailDTO response = bookService.getBook(bookId, userId);
        return ApiResponse.onSuccess(response);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "EPUB 도서 업로드",
            description = "EPUB 파일을 업로드해 도서를 등록합니다. 제목과 저자는 EPUB 메타데이터를 기준으로 저장하며, 업로드 후 비동기 정규화 작업이 시작됩니다."
    )
    public ApiResponse<BookDetailDTO> uploadBook(
            @Parameter(description = "업로드할 EPUB 파일", required = true)
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "예비 제목 값입니다. EPUB 메타데이터에 제목이 없을 때만 사용합니다.", example = "테스트 책")
            @RequestPart(value = "title", required = false) String title,
            @Parameter(description = "예비 저자 값입니다. EPUB 메타데이터에 저자가 없을 때만 사용합니다.", example = "Arthur Conan Doyle")
            @RequestPart(value = "author", required = false) String author,
            @Parameter(description = "예비 언어 코드입니다. EPUB 메타데이터에 언어가 없을 때만 사용합니다.", example = "en")
            @RequestPart(value = "language", required = false) String language) {
        Long userId = requireCurrentUserId();
        BookDetailDTO response = bookService.uploadBook(userId, file, title, author, language);
        return ApiResponse.onSuccess(response);
    }
}
