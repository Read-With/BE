package com.kw.readwith.dto.graph;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kw.readwith.dto.common.LocatorDTO;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EventInfoDTO {
    private Integer chapterIdx;
    
    @JsonProperty("event_id")
    private Integer eventIdx;

    private String eventId;
    
    private LocatorDTO startLocator;
    private LocatorDTO endLocator;

    private Integer startTxtOffset;
    private Integer endTxtOffset;
}
