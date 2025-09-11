package com.kw.readwith.dto.graph;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class FineGraphResponseDTO {
    @JsonProperty("characters")
    private List<GraphNodeDTO> nodes;
    
    @JsonProperty("relations")
    private List<FineGraphEdgeDTO> edges;
    
    @JsonProperty("event")
    private EventInfoDTO eventInfo;
}
