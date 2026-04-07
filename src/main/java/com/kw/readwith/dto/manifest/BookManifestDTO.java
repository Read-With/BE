package com.kw.readwith.dto.manifest;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "매니페스트 안의 도서 메타데이터")
public class BookManifestDTO {

    @Schema(description = "도서 ID", example = "42")
    private Long id;

    @Schema(description = "도서 제목")
    private String title;

    @Schema(description = "저자명")
    private String author;

    @Schema(description = "언어 코드", example = "en")
    private String language;

    @Schema(description = "기본 제공 도서 여부")
    @Getter(AccessLevel.NONE)
    private Boolean isDefault;

    @Schema(description = "요약 준비 완료 여부")
    private Boolean summary;

    @Schema(description = "표지 이미지 URL", nullable = true)
    private String coverImgUrl;

    @Schema(description = "전체 요약 URL", nullable = true)
    private String summaryUrl;

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
    private Boolean needsRenormalization;

    @Schema(description = "정규화 산출물 루트 경로", nullable = true)
    private String normalizedArtifactPath;

    @JsonProperty("isDefault")
    public Boolean getIsDefault() {
        return isDefault;
    }
}
