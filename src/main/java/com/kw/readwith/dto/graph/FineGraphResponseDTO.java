package com.kw.readwith.dto.graph;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(description = "이벤트 단위 그래프 응답")
public class FineGraphResponseDTO {

    @JsonProperty("characters")
    @Schema(description = "이벤트 시점에 등장하는 인물 노드 목록")
    private List<GraphNodeDTO> nodes;

    @JsonProperty("relations")
    @Schema(description = "이벤트 시점의 관계 edge 목록")
    private List<FineGraphEdgeDTO> edges;

    @JsonProperty("event")
    @Schema(description = "현재 그래프가 가리키는 이벤트 정보", nullable = true)
    private EventInfoDTO eventInfo;
}
