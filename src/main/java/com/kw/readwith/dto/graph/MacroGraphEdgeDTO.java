package com.kw.readwith.dto.graph;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(description = "누적 그래프 관계 edge")
public class MacroGraphEdgeDTO {

    @JsonProperty("id1")
    @Schema(description = "출발 인물 characterId", example = "7")
    private Long from;

    @JsonProperty("id2")
    @Schema(description = "도착 인물 characterId", example = "9")
    private Long to;

    @JsonProperty("positivity")
    @Schema(description = "감정 점수", example = "0.75")
    private Double sentimentScore;

    @JsonProperty("count")
    @Schema(description = "상호작용 횟수", example = "12")
    private Integer interactionCount;

    @JsonProperty("relation")
    @Schema(description = "관계 라벨 목록")
    private List<String> relationTags;
}
