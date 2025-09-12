package com.kw.readwith.dto.graph;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class GraphNodeDTO {
    private Long id;
    
    @JsonProperty("common_name")
    private String label;
    
    @JsonProperty("main_character")
    private Boolean isMainCharacter;
    
    private String profileImage;
    private String description;
    
    @JsonProperty("portrait_prompt")
    private String portraitPrompt;
    
    private List<String> names;
    
    // 노드 중요도 (크기)
    private Float weight;
    
    // 참고용 카운트
    private Integer count;
}
