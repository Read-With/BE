package com.kw.readwith.dto.book;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "챕터 POV summary 조회 응답")
public class ChapterPovSummaryResponseDTO {

    @Schema(description = "도서 ID", example = "42")
    private Long bookId;

    @Schema(description = "챕터 인덱스(1-based)", example = "3")
    private Integer chapterIdx;

    @Schema(description = "챕터 제목")
    private String chapterTitle;

    @Schema(description = "인물별 POV summary 목록")
    private List<ChapterPovSummaryDTO> povSummaries;
}
