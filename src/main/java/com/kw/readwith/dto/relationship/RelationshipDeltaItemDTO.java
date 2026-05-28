package com.kw.readwith.dto.relationship;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class RelationshipDeltaItemDTO {

    private Long fromCharacterId;
    private Long toCharacterId;
    private List<String> labels;
    private Double positivity;
    private Integer evidenceCount;
    private String reason;
}
