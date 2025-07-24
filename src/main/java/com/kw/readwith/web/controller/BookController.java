package com.kw.readwith.web.controller;

import com.kw.readwith.dto.book.BookDetailDTO;
import com.kw.readwith.dto.book.BookSummaryDTO;
import com.kw.readwith.service.BookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    // 도서 목록 조회
    @GetMapping
    @Operation(summary = "도서 목록 조회", description = "검색/필터/정렬 파라미터를 이용해 도서 목록을 반환합니다.")
    public ResponseEntity<List<BookSummaryDTO>> getBooks(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String language,
            @RequestParam(defaultValue = "updatedAt") String sort,
            @RequestParam(required = false) Boolean favorite
    ) {
        Long userId = 1L; // TODO 인증된 사용자
        List<BookSummaryDTO> response = bookService.getBooks(q, language, favorite, sort, userId);
        return ResponseEntity.ok(response);
    }

    // 단일 도서 조회
    @GetMapping("/{bookId}")
    @Operation(summary = "단일 도서 조회", description = "도서 ID로 단일 도서를 조회합니다.")
    public ResponseEntity<BookDetailDTO> getBook(@PathVariable Long bookId) {
        Long userId = 1L; // TODO 인증된 사용자
        BookDetailDTO response = bookService.getBook(bookId, userId);
        return ResponseEntity.ok(response);
    }

    // 도서 업로드
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "도서 업로드", description = "EPUB 파일을 업로드하여 도서를 등록합니다.")
    public ResponseEntity<BookDetailDTO> uploadBook(@RequestPart("file") MultipartFile file,
                                                    @RequestPart("title") String title,
                                                    @RequestPart("author") String author,
                                                    @RequestPart("language") String language) {
        Long userId = 1L; // TODO 인증된 사용자
        BookDetailDTO response = bookService.uploadBook(userId, file, title, author, language);
        return ResponseEntity.ok(response);
    }
} 