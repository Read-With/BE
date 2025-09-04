package com.kw.readwith.dto.graph;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class FineGraphEdgeDTO {
    private Long from;
    private Long to;
    private Double weight;
    private Double sentimentScore;
    private Integer interactionCount;
    private List<String> relationTags;
}
