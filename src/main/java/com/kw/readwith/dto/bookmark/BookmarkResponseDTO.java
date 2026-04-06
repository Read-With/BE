package com.kw.readwith.dto.bookmark;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kw.readwith.dto.common.LocatorDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "북마크 응답")
public class BookmarkResponseDTO {

    @Schema(description = "북마크 ID", example = "101")
    private Long id;

    @Schema(description = "도서 ID", example = "42")
    private Long bookId;

    @Schema(description = "북마크 시작 위치")
    private LocatorDTO startLocator;

    @Schema(description = "범위 북마크 종료 위치", nullable = true)
    private LocatorDTO endLocator;

    @Schema(description = "startLocator를 텍스트 오프셋으로 환산한 값", example = "1500")
    private Integer startTxtOffset;

    @Schema(description = "endLocator를 텍스트 오프셋으로 환산한 값", nullable = true)
    private Integer endTxtOffset;

    @Schema(description = "북마크 계산에 사용한 locator 버전", example = "v2")
    private String locatorVersion;

    @Schema(description = "북마크 색상(HEX)", example = "#FFD700", nullable = true)
    private String color;

    @Schema(description = "북마크 메모", nullable = true)
    private String memo;

    @Schema(description = "범위 북마크 여부", example = "true")
    @Getter(AccessLevel.NONE)
    private boolean isRangeBookmark;

    @Schema(description = "생성 시각")
    private LocalDateTime createdAt;

    @Schema(description = "수정 시각")
    private LocalDateTime updatedAt;

    @JsonProperty("isRangeBookmark")
    public boolean isRangeBookmark() {
        return isRangeBookmark;
    }
}
