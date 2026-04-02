package com.kw.readwith.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "인물별 챕터 요약 항목")
public class SummaryItemDTO {

    @Schema(description = "책 내부 characterId", example = "7")
    private String characterId;

    @Schema(description = "인물 이름", nullable = true)
    private String characterName;

    @Schema(description = "해당 인물 시점의 챕터 요약")
    private String summary;
}
