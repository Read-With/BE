package com.kw.readwith.dto.bookmark;

import com.kw.readwith.domain.Bookmark;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class BookmarkResponseDTO {
    private Long id;
    private Long bookId;
    private String startCfi;
    private String endCfi;
    private String color;
    private String memo;
    private boolean isRangeBookmark; // 범위 선택 여부
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static BookmarkResponseDTO from(Bookmark bookmark) {
        return BookmarkResponseDTO.builder()
                .id(bookmark.getId())
                .bookId(bookmark.getBook().getId())
                .startCfi(bookmark.getStartCfi())
                .endCfi(bookmark.getEndCfi())
                .color(bookmark.getColor())
                .memo(bookmark.getMemo())
                .isRangeBookmark(bookmark.isRangeBookmark())
                .createdAt(bookmark.getCreatedAt())
                .updatedAt(bookmark.getUpdatedAt())
                .build();
    }
}
