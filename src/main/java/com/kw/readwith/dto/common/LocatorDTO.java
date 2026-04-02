package com.kw.readwith.dto.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "정규화 본문 안의 읽기 위치를 나타내는 좌표입니다.")
public class LocatorDTO {

    @Schema(description = "1-based 챕터 인덱스", example = "3")
    private Integer chapterIndex;

    @Schema(description = "챕터 안의 블록 인덱스", example = "12")
    private Integer blockIndex;

    @Schema(description = "블록 내부 code point offset", example = "45")
    private Integer offset;
}
