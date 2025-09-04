package com.kw.readwith.dto.graph;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GraphNodeDTO {
    private Long id;
    private String label;
    private Boolean isMainCharacter;
    private String profileImage;
    private String description;
    private String portraitPrompt;
    private String names;
}
