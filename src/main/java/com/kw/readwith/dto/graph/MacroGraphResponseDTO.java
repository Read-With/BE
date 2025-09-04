package com.kw.readwith.dto.graph;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class MacroGraphResponseDTO {
    private List<GraphNodeDTO> nodes;
    private List<MacroGraphEdgeDTO> edges;
    private MacroGraphSummaryDTO summary;
}
