package com.kw.readwith.dto.graph;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EventInfoDTO {
    private Integer chapterIdx;
    
    @JsonProperty("event_id")
    private Integer eventIdx;
    
    private Integer start;
    private Integer end;
}
