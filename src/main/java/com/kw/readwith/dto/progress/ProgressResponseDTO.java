package com.kw.readwith.dto.progress;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kw.readwith.dto.common.LocatorDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "독서 진도 응답")
public class ProgressResponseDTO {

    @Schema(description = "도서 ID", example = "42")
    private Long bookId;

    @Schema(description = "현재 저장된 시작 위치")
    private LocatorDTO startLocator;

    @Schema(description = "범위 진도를 지원할 때 사용할 종료 위치. 현재는 null입니다.", nullable = true)
    private LocatorDTO endLocator;

    @Schema(description = "startLocator를 텍스트 오프셋으로 환산한 값", example = "1280", nullable = true)
    private Integer startTxtOffset;

    @Schema(description = "endLocator를 텍스트 오프셋으로 환산한 값. 현재는 null입니다.", nullable = true)
    private Integer endTxtOffset;

    @Schema(description = "현재 도서에 적용된 locator 버전", example = "v2")
    private String locatorVersion;

    @Schema(description = "마지막 저장 시각")
    private LocalDateTime updatedAt;

    @JsonProperty("locator")
    @Schema(description = "legacy 응답 필드 alias입니다. 값은 startLocator와 같습니다.", deprecated = true)
    public LocatorDTO getLocator() {
        return startLocator;
    }
}
