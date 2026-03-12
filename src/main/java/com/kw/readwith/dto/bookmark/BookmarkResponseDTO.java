package com.kw.readwith.dto.bookmark;

import com.kw.readwith.dto.common.LocatorDTO;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class BookmarkResponseDTO {
    private Long id;
    private Long bookId;
    private LocatorDTO startLocator;
    private LocatorDTO endLocator;
    private Integer startTxtOffset;
    private Integer endTxtOffset;
    private String locatorVersion;
    private String color;
    private String memo;
    private boolean isRangeBookmark; // 범위 선택 여부
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
