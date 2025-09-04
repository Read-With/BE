package com.kw.readwith.dto.graph;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MacroGraphSummaryDTO {
    private Integer uptoChapter;
    private Integer totalCharacters;
    private Integer totalRelationships;
}
