package com.kw.readwith.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class EventDTO {

    private Integer start;
    private Integer end;
    // text 필드 제거 (사용하지 않음)

    @JsonProperty("event_id")
    private Integer eventId;

}
    