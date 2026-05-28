package com.kw.readwith.dto.admin;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@Schema(description = "관계 edge 1건에 대한 업로드 항목")
public class RelationshipDTO {

    @JsonAlias("id1")
    @Schema(description = "출발 인물 characterId", example = "7")
    private String fromCharacterId;

    @JsonAlias("id2")
    @Schema(description = "도착 인물 characterId", example = "9")
    private String toCharacterId;

    @JsonAlias("relation")
    @Schema(description = "관계 라벨 목록", example = "[\"동료\", \"협력\"]")
    private List<String> labels;

    @Schema(description = "감정 점수", example = "0.72")
    private Double positivity;

    @JsonAlias("count")
    @Schema(description = "근거 개수 또는 상호작용 횟수", example = "3")
    private Integer evidenceCount;

    @Schema(description = "현재 이벤트에서 관계 변화가 발생했다고 판단한 근거")
    private String reason;
}
