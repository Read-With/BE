package com.kw.readwith.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Builder
@Schema(description = "AI 분석 서버가 내려받을 정규화 산출물 export 응답")
public class AnalysisInputResponseDTO {

    @Schema(description = "도서 ID", example = "42")
    private Long bookId;

    @Schema(description = "도서 제목")
    private String title;

    @Schema(description = "저자명")
    private String author;

    @Schema(description = "언어 코드", example = "en")
    private String language;

    @Schema(description = "활성 정규화 run id", nullable = true)
    private String normalizationRunId;

    @Schema(description = "정규화 산출물 루트 경로", nullable = true)
    private String normalizedArtifactPath;

    @Schema(description = "정규화 규칙 버전", nullable = true)
    private String ruleVersion;

    @Schema(description = "locator 버전", nullable = true)
    private String locatorVersion;

    @Schema(description = "meta.json 다운로드용 presigned URL")
    private String metaUrl;

    @Schema(description = "URL 만료 시각", example = "2026-04-02T15:30:00+09:00")
    private OffsetDateTime expiresAt;

    @Schema(description = "챕터별 txt 다운로드 정보")
    private List<AnalysisInputChapterDTO> chapters;
}
