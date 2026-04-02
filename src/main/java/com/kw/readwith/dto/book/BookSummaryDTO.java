package com.kw.readwith.dto.book;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "도서 목록 응답에서 사용하는 요약 정보")
public class BookSummaryDTO {

    @Schema(description = "도서 ID", example = "42")
    private Long id;

    @Schema(description = "도서 제목", example = "Sherlock Holmes")
    private String title;

    @Schema(description = "저자명", example = "Arthur Conan Doyle")
    private String author;

    @Schema(description = "표지 이미지 URL", nullable = true)
    private String coverImgUrl;

    @Schema(description = "원본 EPUB 저장 경로", nullable = true)
    private String epubPath;

    @Schema(description = "정규화 상태", example = "READY")
    private String normalizationStatus;

    @Schema(description = "분석 상태", example = "READY")
    private String analysisStatus;

    @Schema(description = "정규화 규칙 버전", nullable = true)
    private String ruleVersion;

    @Schema(description = "locator 버전", nullable = true)
    private String locatorVersion;

    @Schema(description = "활성 정규화 run id", nullable = true)
    private String normalizationRunId;

    @Schema(description = "정규화 버전 비교 상태", nullable = true)
    private String normalizationVersionStatus;

    @Schema(description = "재정규화 필요 여부")
    private boolean needsRenormalization;

    @Schema(description = "정규화 산출물 루트 경로", nullable = true)
    private String normalizedArtifactPath;

    @Schema(description = "현재 사용자 기준 즐겨찾기 여부")
    private boolean isFavorite;

    @Schema(description = "기본 제공 도서 여부")
    private boolean isDefault;

    @Schema(description = "요약 준비 완료 여부")
    private boolean summary;

    @Schema(description = "마지막 수정 시각")
    private LocalDateTime updatedAt;
}
