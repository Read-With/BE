package com.kw.readwith.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class EventDTO {

    private Integer chapterIndex;

    private Integer start;
    private Integer end;
    private Integer startTxtOffset;
    private Integer endTxtOffset;

    private String text;
    private String eventText;

    @JsonProperty("event_id")
    private String eventId;

}
    
