package com.kw.readwith.dto.book;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class BookSummaryDTO {
    /** 책 식별자 */
    private Long id;
    /** 책 목록 제목 */
    private String title;
    /** 책 목록 저자 */
    private String author;
    /** 책 카드 커버 이미지 */
    private String coverImgUrl;
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
    /** 재정규화가 필요한 책인지 표시 */
    private boolean needsRenormalization;
    /** 활성 정규화 산출물 루트 경로 */
    private String normalizedArtifactPath;
    /** 현재 사용자의 즐겨찾기 여부 */
    private boolean isFavorite;
    /** 기본 제공 책인지 여부 */
    private boolean isDefault;
    /** 요약 자료 준비 여부 */
    private boolean summary;
    /** 목록 정렬에 쓰는 최종 수정 시각 */
    private LocalDateTime updatedAt;
}
