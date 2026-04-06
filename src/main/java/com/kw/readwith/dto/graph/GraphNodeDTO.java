package com.kw.readwith.dto.graph;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(description = "Graph character node")
public class GraphNodeDTO {

    @Schema(description = "Character ID", example = "7")
    private Long id;

    @JsonProperty("common_name")
    @Schema(description = "Common character name")
    private String label;

    @Getter(AccessLevel.NONE)
    @Schema(description = "Whether the character is a main character")
    private Boolean isMainCharacter;

    @Schema(description = "Profile image URL", nullable = true)
    private String profileImage;

    @Schema(description = "Character description", nullable = true)
    private String description;

    @JsonProperty("portrait_prompt")
    @Schema(description = "Portrait prompt text", nullable = true)
    private String portraitPrompt;

    @Schema(description = "Character names and aliases")
    private List<String> names;

    @Schema(description = "Node weight in the current event or cumulative view", nullable = true)
    private Float weight;

    @Schema(description = "Occurrence count in the cumulative graph", nullable = true)
    private Integer count;

    @JsonProperty("isMainCharacter")
    public Boolean getIsMainCharacter() {
        return isMainCharacter;
    }
}
