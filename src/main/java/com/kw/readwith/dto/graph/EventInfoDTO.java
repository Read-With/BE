package com.kw.readwith.dto.graph;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kw.readwith.dto.common.LocatorDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "그래프가 가리키는 이벤트 정보")
public class EventInfoDTO {

    @Schema(description = "챕터 인덱스", example = "3")
    private Integer chapterIdx;

    @JsonProperty("event_id")
    @Schema(description = "챕터 내부 이벤트 인덱스", example = "4")
    private Integer eventIdx;

    @Schema(description = "이벤트 식별자", example = "ch3-e4")
    private String eventId;

    @Schema(description = "이벤트 시작 locator", nullable = true)
    private LocatorDTO startLocator;

    @Schema(description = "이벤트 종료 locator", nullable = true)
    private LocatorDTO endLocator;

    @Schema(description = "이벤트 시작 텍스트 오프셋", nullable = true)
    private Integer startTxtOffset;

    @Schema(description = "이벤트 종료 텍스트 오프셋", nullable = true)
    private Integer endTxtOffset;
}
