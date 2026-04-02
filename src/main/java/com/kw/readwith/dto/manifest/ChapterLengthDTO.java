package com.kw.readwith.dto.manifest;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "한 챕터의 길이 정보")
public class ChapterLengthDTO {

    @Schema(description = "챕터 인덱스", example = "3")
    private Integer chapterIdx;

    @Schema(description = "해당 챕터 길이", example = "4210")
    private Integer length;
}
