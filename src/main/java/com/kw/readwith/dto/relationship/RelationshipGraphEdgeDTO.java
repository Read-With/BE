package com.kw.readwith.dto.relationship;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class RelationshipGraphEdgeDTO {

    @JsonProperty("id1")
    private Long from;

    @JsonProperty("id2")
    private Long to;

    @JsonProperty("positivity")
    private Double positivity;

    @JsonProperty("count")
    private Integer evidenceCount;

    @JsonProperty("relation")
    private List<String> labels;

    private Map<String, Integer> labelScores;
    private List<String> latestLabels;
    private String latestReason;
    private String latestEventId;
    private Map<String, Integer> directionCounts;
}
