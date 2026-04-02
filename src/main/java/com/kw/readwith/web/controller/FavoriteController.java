package com.kw.readwith.web.controller;

import com.kw.readwith.apiPayload.ApiResponse;
import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.dto.book.BookSummaryDTO;
import com.kw.readwith.service.FavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping({"/api/favorites", "/api/v2/favorites"})
@RequiredArgsConstructor
@Tag(name = "Favorites", description = "즐겨찾기 관련 API")
public class FavoriteController {

    private final FavoriteService favoriteService;

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Long) {
            return (Long) authentication.getPrincipal();
        }
        throw new GeneralException(ErrorStatus._UNAUTHORIZED);
    }

    // 즐겨찾기 추가
    @PostMapping("/{bookId}")
    @Operation(summary = "즐겨찾기 추가", description = "도서를 즐겨찾기 목록에 추가합니다.")
    public ApiResponse<Void> addFavorite(@PathVariable Long bookId) {
        Long userId = getCurrentUserId();
        favoriteService.addFavorite(userId, bookId);
        return ApiResponse.onSuccess(null);
    }

    // 즐겨찾기 삭제
    @DeleteMapping("/{bookId}")
    @Operation(summary = "즐겨찾기 삭제", description = "즐겨찾기 목록에서 도서를 제거합니다.")
    public ApiResponse<Void> removeFavorite(@PathVariable Long bookId) {
        Long userId = getCurrentUserId();
        favoriteService.removeFavorite(userId, bookId);
        return ApiResponse.onSuccess(null);
    }

    // 즐겨찾기 목록 조회
    @GetMapping
    @Operation(summary = "즐겨찾기 목록 조회", description = "사용자의 즐겨찾기 도서 목록을 조회합니다.")
    public ApiResponse<List<BookSummaryDTO>> getFavorites() {
        Long userId = getCurrentUserId();
        List<BookSummaryDTO> response = favoriteService.getFavoriteBooks(userId);
        return ApiResponse.onSuccess(response);
    }
} 
