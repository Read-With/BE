package com.kw.readwith.dto.relationship;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kw.readwith.dto.graph.GraphNodeDTO;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class RelationshipGraphResponseDTO {

    private Long bookId;
    private String scope;
    private Integer chapterIndex;
    private String eventId;

    @JsonProperty("characters")
    private List<GraphNodeDTO> nodes;

    @JsonProperty("relations")
    private List<RelationshipGraphEdgeDTO> edges;
}
