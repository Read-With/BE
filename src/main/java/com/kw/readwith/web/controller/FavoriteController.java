package com.kw.readwith.web.controller;

import com.kw.readwith.apiPayload.ApiResponse;
import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.dto.book.BookSummaryDTO;
import com.kw.readwith.service.FavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/api/favorites", "/api/v2/favorites"})
@RequiredArgsConstructor
@Tag(name = "즐겨찾기", description = "사용자별 즐겨찾기 도서 관리 API입니다.")
public class FavoriteController {

    private final FavoriteService favoriteService;

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Long) {
            return (Long) authentication.getPrincipal();
        }
        throw new GeneralException(ErrorStatus._UNAUTHORIZED);
    }

    @PostMapping("/{bookId}")
    @Operation(summary = "즐겨찾기 추가", description = "특정 도서를 현재 사용자의 즐겨찾기 목록에 추가합니다.")
    public ApiResponse<Void> addFavorite(
            @Parameter(description = "즐겨찾기에 추가할 도서 ID", required = true)
            @PathVariable Long bookId) {
        Long userId = getCurrentUserId();
        favoriteService.addFavorite(userId, bookId);
        return ApiResponse.onSuccess(null);
    }

    @DeleteMapping("/{bookId}")
    @Operation(summary = "즐겨찾기 삭제", description = "특정 도서를 현재 사용자의 즐겨찾기 목록에서 제거합니다.")
    public ApiResponse<Void> removeFavorite(
            @Parameter(description = "즐겨찾기에서 제거할 도서 ID", required = true)
            @PathVariable Long bookId) {
        Long userId = getCurrentUserId();
        favoriteService.removeFavorite(userId, bookId);
        return ApiResponse.onSuccess(null);
    }

    @GetMapping
    @Operation(summary = "즐겨찾기 목록 조회", description = "현재 사용자가 즐겨찾기한 도서 목록을 조회합니다.")
    public ApiResponse<List<BookSummaryDTO>> getFavorites() {
        Long userId = getCurrentUserId();
        List<BookSummaryDTO> response = favoriteService.getFavoriteBooks(userId);
        return ApiResponse.onSuccess(response);
    }
}
