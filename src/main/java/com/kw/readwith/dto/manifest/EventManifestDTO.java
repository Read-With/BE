package com.kw.readwith.dto.manifest;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EventManifestDTO {
    
    /**
     * 이벤트 인덱스 (챕터 내)
     */
    private Integer idx;
    
    /**
     * 시작 위치
     */
    private Integer startPos;
    
    /**
     * 종료 위치
     */
    private Integer endPos;
    
    /**
     * 원본 텍스트
     */
    private String rawText;
}
