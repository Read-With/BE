package com.kw.readwith.web.controller;

import com.kw.readwith.dto.book.BookDetailDTO;
import com.kw.readwith.dto.admin.UnsummarizedItemDTO;
import com.kw.readwith.dto.book.BookSummaryDTO;
import com.kw.readwith.service.BookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "관리자 관련 API")
public class AdminController {

    private final BookService bookService;

    @GetMapping("/books/unsummarized")
    @Operation(summary = "요약되지 않은 도서 목록 조회", description = "AI 요약이 아직 생성되지 않은 도서들의 목록을 반환합니다.")
    public ResponseEntity<List<BookSummaryDTO>> getUnsummarizedBooks() {
        List<BookSummaryDTO> response = bookService.getUnsummarizedBooks();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/chapters/unsummarized")
    @Operation(summary = "요약되지 않은 챕터 목록 조회", description = "AI 요약이 아직 생성되지 않은 챕터들의 목록을 반환합니다.")
    public ResponseEntity<List<UnsummarizedItemDTO>> getUnsummarizedChapters() {
        List<UnsummarizedItemDTO> response = bookService.getUnsummarizedChapters();
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/books/{bookId}/chapters/{chapter_id}/summary", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "챕터 요약 업로드 (bookId와 chapter_id 사용)", description = "특정 책(bookId)의 특정 챕터(chapter_id)에 대한 요약 JSON 파일을 S3에 업로드하고, 챕터 정보를 업데이트합니다.")
    public ResponseEntity<Void> uploadChapterSummary(
            @PathVariable Long bookId,
            @PathVariable(name = "chapter_id") Integer chapterId,
            @RequestPart("summaryFile") MultipartFile summaryFile) {
        bookService.uploadChapterSummary(bookId, chapterId, summaryFile);
        return ResponseEntity.ok().build();
    }
}