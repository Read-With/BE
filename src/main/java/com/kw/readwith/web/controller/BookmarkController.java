package com.kw.readwith.web.controller;

import com.kw.readwith.apiPayload.ApiResponse;
import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.dto.bookmark.BookmarkResponseDTO;
import com.kw.readwith.dto.bookmark.CreateBookmarkRequestDTO;
import com.kw.readwith.dto.bookmark.UpdateBookmarkRequestDTO;
import com.kw.readwith.service.BookmarkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/bookmarks", "/api/v2/bookmarks"})
@RequiredArgsConstructor
@Tag(name = "Bookmarks", description = "북마크 관련 API")
public class BookmarkController {

    private final BookmarkService bookmarkService;

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Long) {
            return (Long) authentication.getPrincipal();
        }
        throw new GeneralException(ErrorStatus._UNAUTHORIZED);
    }

    /**
     * 북마크 목록 조회
     */
    @GetMapping
    @Operation(summary = "북마크 목록 조회", description = "특정 책에 대한 사용자의 북마크 목록을 조회합니다.")
    public ApiResponse<List<BookmarkResponseDTO>> getBookmarks(
            @Parameter(description = "책 ID", required = true)
            @RequestParam Long bookId,
            @Parameter(description = "정렬 방식 (time_desc: 최신순, time_asc: 오래된순)", example = "time_desc")
            @RequestParam(defaultValue = "time_desc") String sort) {
        
        Long userId = getCurrentUserId();
        List<BookmarkResponseDTO> bookmarks = bookmarkService.getBookmarks(userId, bookId, sort);
        return ApiResponse.onSuccess(bookmarks);
    }

    /**
     * 북마크 생성
     */
    @PostMapping
    @Operation(summary = "북마크 생성", description = "새로운 북마크를 생성합니다. startLocator는 필수이며, endLocator는 범위 선택 시에만 제공합니다.")
    public ApiResponse<BookmarkResponseDTO> createBookmark(
            @Valid @RequestBody CreateBookmarkRequestDTO requestDTO) {
        
        Long userId = getCurrentUserId();
        BookmarkResponseDTO bookmark = bookmarkService.createBookmark(userId, requestDTO);
        return ApiResponse.onSuccess(bookmark);
    }

    /**
     * 북마크 수정
     */
    @PatchMapping("/{bookmarkId}")
    @Operation(summary = "북마크 수정", description = "기존 북마크의 색상이나 메모를 수정합니다.")
    public ApiResponse<BookmarkResponseDTO> updateBookmark(
            @Parameter(description = "북마크 ID", required = true)
            @PathVariable Long bookmarkId,
            @Valid @RequestBody UpdateBookmarkRequestDTO requestDTO) {
        
        Long userId = getCurrentUserId();
        BookmarkResponseDTO bookmark = bookmarkService.updateBookmark(userId, bookmarkId, requestDTO);
        return ApiResponse.onSuccess(bookmark);
    }

    /**
     * 북마크 삭제
     */
    @DeleteMapping("/{bookmarkId}")
    @Operation(summary = "북마크 삭제", description = "기존 북마크를 삭제합니다.")
    public ApiResponse<Void> deleteBookmark(
            @Parameter(description = "북마크 ID", required = true)
            @PathVariable Long bookmarkId) {
        
        Long userId = getCurrentUserId();
        bookmarkService.deleteBookmark(userId, bookmarkId);
        return ApiResponse.onSuccess(null);
    }
}
