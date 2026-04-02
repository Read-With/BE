package com.kw.readwith.dto.graph;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(description = "누적 그래프 응답")
public class MacroGraphResponseDTO {

    @JsonProperty("characters")
    @Schema(description = "누적 시점까지 등장한 인물 노드 목록")
    private List<GraphNodeDTO> nodes;

    @JsonProperty("relations")
    @Schema(description = "누적 시점까지 계산된 관계 edge 목록")
    private List<MacroGraphEdgeDTO> edges;

    @Schema(description = "현재 누적 기준 챕터 인덱스", example = "3")
    private Integer userCurrentChapter;
}
