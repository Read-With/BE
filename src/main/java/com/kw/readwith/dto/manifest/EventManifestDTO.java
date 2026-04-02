package com.kw.readwith.dto.manifest;

import com.kw.readwith.dto.common.LocatorDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "매니페스트 안의 이벤트 정보")
public class EventManifestDTO {

    @Schema(description = "챕터 내부 이벤트 인덱스", example = "4")
    private Integer idx;

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

    @Schema(description = "이벤트 원문 일부", nullable = true)
    private String rawText;
}
