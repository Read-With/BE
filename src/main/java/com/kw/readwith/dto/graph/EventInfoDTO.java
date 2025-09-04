package com.kw.readwith.dto.graph;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EventInfoDTO {
    private Integer chapterIdx;
    private Integer eventIdx;
    private String eventText;
}
