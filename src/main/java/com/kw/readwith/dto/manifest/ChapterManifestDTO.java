package com.kw.readwith.dto.manifest;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ChapterManifestDTO {
    
    /**
     * 챕터 인덱스 (1-based)
     */
    private Integer idx;
    
    /**
     * 챕터 제목
     */
    private String title;

    /**
     * EPUB spine href
     */
    private String spineHref;

    /**
     * 문단 수
     */
    private Integer paragraphCount;

    /**
     * 문단 시작 오프셋 JSON
     */
    private String paragraphStartsJson;

    /**
     * 문단 길이 JSON
     */
    private String paragraphLengthsJson;

    /**
     * 챕터 전체 code point 수
     */
    private Integer totalCodePoints;
    
    /**
     * 시작 위치
     */
    private Integer startPos;
    
    /**
     * 종료 위치
     */
    private Integer endPos;
    
    /**
     * 원본 텍스트 (일부)
     */
    private String rawText;
    
    /**
     * 챕터 요약
     */
    private String summaryText;
    
    /**
     * 요약 업로드 URL
     */
    private String summaryUploadUrl;
    
    /**
     * POV 요약 캐시 여부
     */
    private Boolean povSummariesCached;
    
    /**
     * 이벤트 목록
     */
    private List<EventManifestDTO> events;
}
