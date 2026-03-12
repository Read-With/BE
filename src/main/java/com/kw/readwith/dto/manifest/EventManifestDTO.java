package com.kw.readwith.dto.manifest;

import com.kw.readwith.dto.common.LocatorDTO;
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
     * 이벤트 표준 식별자
     */
    private String eventId;
    
    /**
     * 시작 locator
     */
    private LocatorDTO startLocator;
    
    /**
     * 종료 locator
     */
    private LocatorDTO endLocator;

    /**
     * 시작 텍스트 오프셋
     */
    private Integer startTxtOffset;

    /**
     * 종료 텍스트 오프셋
     */
    private Integer endTxtOffset;
    
    /**
     * 원본 텍스트
     */
    private String rawText;
}
