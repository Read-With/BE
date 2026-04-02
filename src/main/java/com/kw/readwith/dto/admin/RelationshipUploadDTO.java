package com.kw.readwith.dto.admin;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Getter
@NoArgsConstructor
public class RelationshipUploadDTO {

    private Integer chapterIndex;
    private String eventId;

    @JsonAlias("relations")
    private List<RelationshipDTO> items;

    @JsonAlias("node_weights_accum")
    private Map<String, NodeWeightDTO> nodeWeights;
}
