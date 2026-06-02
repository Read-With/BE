package com.kw.readwith.dto.relationship;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class RelationshipDeltaListResponseDTO {

    private Long bookId;
    private List<RelationshipDeltaEventDTO> deltas;
}
