package com.kw.readwith.dto.graph;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MacroGraphEdgeDTO {
    private Long from;
    private Long to;
    private Integer cumulativeInteraction;
    private Double cumulativeSentiment;
    private Double sentimentWeightedSum;
    private Integer chapterIdx;
}
