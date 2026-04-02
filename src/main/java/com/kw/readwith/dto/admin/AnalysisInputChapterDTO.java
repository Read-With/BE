package com.kw.readwith.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "AI 입력으로 전달할 챕터별 정규화 본문 정보")
public class AnalysisInputChapterDTO {

    @Schema(description = "챕터 인덱스(1-based)", example = "3")
    private Integer chapterIndex;

    @Schema(description = "챕터 제목", nullable = true)
    private String title;

    @Schema(description = "정규화된 챕터 원문 txt 다운로드용 presigned URL")
    private String txtUrl;
}
