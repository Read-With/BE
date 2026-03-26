package com.kw.readwith.dto.manifest;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BookManifestDTO {

    /** 책 식별자 */
    private Long id;
    /** 리더에 표시할 제목 */
    private String title;
    /** 리더에 표시할 저자 */
    private String author;
    /** 책 언어 코드 */
    private String language;
    /** 기본 제공 책인지 여부 */
    private Boolean isDefault;
    /** 요약 자료 준비 여부 */
    private Boolean summary;
    /** 리더용 커버 이미지 */
    private String coverImgUrl;
    /** 책 전체 요약 경로 */
    private String summaryUrl;
    /** 원본 EPUB 경로 */
    private String epubPath;
    /** 본문 읽기 가능 상태 */
    private String normalizationStatus;
    /** AI 분석 자료 상태 */
    private String analysisStatus;
    /** 현재 활성 정규화 규칙 버전 */
    private String ruleVersion;
    /** 현재 활성 locator 버전 */
    private String locatorVersion;
    /** 현재 활성 정규화 run id */
    private String normalizationRunId;
    /** 현재 엔진 기준 버전 상태 */
    private String normalizationVersionStatus;
    /** 재정규화 필요 여부 */
    private Boolean needsRenormalization;
    /** 활성 정규화 산출물 루트 경로 */
    private String normalizedArtifactPath;
}
