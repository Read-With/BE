package com.kw.readwith.dto.graph;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class FineGraphResponseDTO {
    private List<GraphNodeDTO> nodes;
    private List<FineGraphEdgeDTO> edges;
    private EventInfoDTO eventInfo;
}
