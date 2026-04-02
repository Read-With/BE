package com.kw.readwith.dto.manifest;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "진도 계산에 필요한 챕터 길이 메타데이터")
public class ProgressMetadataDTO {

    @Schema(description = "가장 마지막 챕터 인덱스", example = "30")
    private Integer maxChapter;

    @Schema(description = "챕터별 길이 목록")
    private List<ChapterLengthDTO> chapterLengths;

    @Schema(description = "도서 전체 길이", example = "125430")
    private Integer totalLength;
}
