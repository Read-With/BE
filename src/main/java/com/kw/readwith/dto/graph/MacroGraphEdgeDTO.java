package com.kw.readwith.dto.graph;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class MacroGraphEdgeDTO {
    @JsonProperty("id1")
    private Long from;
    
    @JsonProperty("id2")
    private Long to;
    
    private Double weight;
    
    @JsonProperty("positivity")
    private Double sentimentScore;
    
    @JsonProperty("count")
    private Integer interactionCount;
    
    @JsonProperty("relation")
    private List<String> relationTags;
}
