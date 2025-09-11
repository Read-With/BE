package com.kw.readwith.dto.graph;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class MacroGraphResponseDTO {
    @JsonProperty("characters")
    private List<GraphNodeDTO> nodes;
    
    @JsonProperty("relations")
    private List<MacroGraphEdgeDTO> edges;
    
    private Integer userCurrentChapter; // 사용자 현재 진도 챕터 (스포일러 방지용)
}
