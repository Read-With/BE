package com.kw.readwith.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
public class RelationshipUploadDTO {
    private List<RelationshipDTO> relations;

    @JsonProperty("node_weights_accum")
    private Map<String, NodeWeightDTO> nodeWeightsAccum;

    private Map<String, Object> log;
}
