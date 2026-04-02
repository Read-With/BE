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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/api/bookmarks", "/api/v2/bookmarks"})
@RequiredArgsConstructor
@Tag(name = "북마크", description = "사용자별 북마크 관리 API입니다.")
public class BookmarkController {

    private final BookmarkService bookmarkService;

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Long) {
            return (Long) authentication.getPrincipal();
        }
        throw new GeneralException(ErrorStatus._UNAUTHORIZED);
    }

    @GetMapping
    @Operation(summary = "북마크 목록 조회", description = "특정 책에 대해 저장한 북마크 목록을 조회합니다.")
    public ApiResponse<List<BookmarkResponseDTO>> getBookmarks(
            @Parameter(description = "조회할 도서 ID", required = true, example = "42")
            @RequestParam Long bookId,
            @Parameter(description = "정렬 방식. `time_desc` 또는 `time_asc`를 사용합니다.", example = "time_desc")
            @RequestParam(defaultValue = "time_desc") String sort) {

        Long userId = getCurrentUserId();
        List<BookmarkResponseDTO> bookmarks = bookmarkService.getBookmarks(userId, bookId, sort);
        return ApiResponse.onSuccess(bookmarks);
    }

    @PostMapping
    @Operation(
            summary = "북마크 생성",
            description = "새 북마크를 생성합니다. `startLocator`는 필수이고, `endLocator`를 함께 보내면 범위 북마크로 저장합니다."
    )
    public ApiResponse<BookmarkResponseDTO> createBookmark(
            @Valid @RequestBody CreateBookmarkRequestDTO requestDTO) {

        Long userId = getCurrentUserId();
        BookmarkResponseDTO bookmark = bookmarkService.createBookmark(userId, requestDTO);
        return ApiResponse.onSuccess(bookmark);
    }

    @PatchMapping("/{bookmarkId}")
    @Operation(summary = "북마크 수정", description = "기존 북마크의 색상과 메모를 수정합니다.")
    public ApiResponse<BookmarkResponseDTO> updateBookmark(
            @Parameter(description = "수정할 북마크 ID", required = true, example = "101")
            @PathVariable Long bookmarkId,
            @Valid @RequestBody UpdateBookmarkRequestDTO requestDTO) {

        Long userId = getCurrentUserId();
        BookmarkResponseDTO bookmark = bookmarkService.updateBookmark(userId, bookmarkId, requestDTO);
        return ApiResponse.onSuccess(bookmark);
    }

    @DeleteMapping("/{bookmarkId}")
    @Operation(summary = "북마크 삭제", description = "북마크를 삭제합니다.")
    public ApiResponse<Void> deleteBookmark(
            @Parameter(description = "삭제할 북마크 ID", required = true, example = "101")
            @PathVariable Long bookmarkId) {

        Long userId = getCurrentUserId();
        bookmarkService.deleteBookmark(userId, bookmarkId);
        return ApiResponse.onSuccess(null);
    }
}
