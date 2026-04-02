package com.kw.readwith.dto.graph;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(description = "그래프의 인물 노드")
public class GraphNodeDTO {

    @Schema(description = "책 내부 characterId", example = "7")
    private Long id;

    @JsonProperty("common_name")
    @Schema(description = "대표 이름")
    private String label;

    @JsonProperty("main_character")
    @Schema(description = "주요 인물 여부")
    private Boolean isMainCharacter;

    @Schema(description = "프로필 이미지 URL", nullable = true)
    private String profileImage;

    @Schema(description = "인물 설명", nullable = true)
    private String description;

    @JsonProperty("portrait_prompt")
    @Schema(description = "프로필/프롬프트 텍스트", nullable = true)
    private String portraitPrompt;

    @Schema(description = "이름/별칭 목록")
    private List<String> names;

    @Schema(description = "현재 이벤트 또는 누적 시점에서의 node weight", nullable = true)
    private Float weight;

    @Schema(description = "누적 그래프에서 등장 횟수", nullable = true)
    private Integer count;
}
