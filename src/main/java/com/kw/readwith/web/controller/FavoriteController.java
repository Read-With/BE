package com.kw.readwith.web.controller;

import com.kw.readwith.dto.book.BookSummaryDTO;
import com.kw.readwith.service.FavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
@Tag(name = "Favorites", description = "즐겨찾기 관련 API")
public class FavoriteController {

    private final FavoriteService favoriteService;

    // 즐겨찾기 추가
    @PostMapping("/{bookId}")
    @Operation(summary = "즐겨찾기 추가", description = "도서를 즐겨찾기 목록에 추가합니다.")
    public ResponseEntity<Void> addFavorite(@PathVariable Long bookId) {
        Long userId = 1L; // TODO 인증된 사용자
        favoriteService.addFavorite(userId, bookId);
        return ResponseEntity.ok().build();
    }

    // 즐겨찾기 삭제
    @DeleteMapping("/{bookId}")
    @Operation(summary = "즐겨찾기 삭제", description = "즐겨찾기 목록에서 도서를 제거합니다.")
    public ResponseEntity<Void> removeFavorite(@PathVariable Long bookId) {
        Long userId = 1L; // TODO 인증된 사용자
        favoriteService.removeFavorite(userId, bookId);
        return ResponseEntity.noContent().build();
    }

    // 즐겨찾기 목록 조회
    @GetMapping
    @Operation(summary = "즐겨찾기 목록 조회", description = "사용자의 즐겨찾기 도서 목록을 조회합니다.")
    public ResponseEntity<List<BookSummaryDTO>> getFavorites() {
        Long userId = 1L; // TODO 인증된 사용자
        List<BookSummaryDTO> response = favoriteService.getFavoriteBooks(userId);
        return ResponseEntity.ok(response);
    }
} 