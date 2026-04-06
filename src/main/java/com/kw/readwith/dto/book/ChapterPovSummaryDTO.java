package com.kw.readwith.dto.book;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "한 인물에 대한 챕터 POV summary")
public class ChapterPovSummaryDTO {

    @Schema(description = "책 내부 characterId", example = "7")
    private Long characterId;

    @Schema(description = "인물 이름", example = "셜록 홈즈")
    private String characterName;

    @Schema(description = "해당 인물 시점의 챕터 요약")
    private String summaryText;

    @Schema(description = "주요 인물 여부")
    @Getter(AccessLevel.NONE)
    private boolean isMainCharacter;

    @JsonProperty("isMainCharacter")
    public boolean isMainCharacter() {
        return isMainCharacter;
    }
}
