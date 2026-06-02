package com.kw.readwith.dto.relationship;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class RelationshipDeltaEventDTO {

    private String contractVersion;
    private Integer chapterIndex;
    private String eventId;
    private List<RelationshipDeltaItemDTO> items;
    private Map<String, RelationshipNodeWeightDTO> nodeWeights;
}
