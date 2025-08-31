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
