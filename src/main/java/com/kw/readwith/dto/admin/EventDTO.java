package com.kw.readwith.dto.admin;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "이벤트 1건에 대한 업로드 항목")
public class EventDTO {

    @Schema(description = "아이템 내부 chapterIndex. 루트 chapterIndex와 같아야 합니다.", nullable = true)
    private Integer chapterIndex;

    @Schema(description = "legacy 시작 offset", nullable = true, deprecated = true)
    private Integer start;

    @Schema(description = "legacy 종료 offset", nullable = true, deprecated = true)
    private Integer end;

    @Schema(description = "이벤트 시작 텍스트 오프셋", example = "1200")
    private Integer startTxtOffset;

    @Schema(description = "이벤트 종료 텍스트 오프셋", example = "1368")
    private Integer endTxtOffset;

    @Schema(description = "legacy 이벤트 텍스트", nullable = true, deprecated = true)
    private String text;

    @Schema(description = "이벤트 원문. chapterTxt[start:end]와 정확히 일치해야 합니다.")
    private String eventText;

    @JsonAlias("event_id")
    @Schema(description = "이벤트 식별자", example = "ch3-e4")
    private String eventId;
}
