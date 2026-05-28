package com.kw.readwith.dto.admin;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Getter
@NoArgsConstructor
@Schema(description = "관계 업로드 payload 루트")
public class RelationshipUploadDTO {

    @Schema(description = "relationship delta contract version", example = "relationship-delta-v1")
    private String contractVersion;

    @Schema(description = "파일이 담당하는 챕터 인덱스(1-based)", example = "3")
    private Integer chapterIndex;

    @Schema(description = "대상 이벤트 식별자", example = "ch3-e4")
    private String eventId;

    @JsonAlias("relations")
    @Schema(description = "관계 edge 목록")
    private List<RelationshipDTO> items;

    @JsonAlias("node_weights_accum")
    @Schema(description = "characterId별 node weight 정보")
    private Map<String, NodeWeightDTO> nodeWeights;
}
