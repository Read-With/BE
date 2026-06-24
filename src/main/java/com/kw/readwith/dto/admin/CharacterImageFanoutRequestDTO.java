package com.kw.readwith.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "승인된 대표 이미지를 기준으로 파생 캐릭터 이미지를 생성하는 요청")
public class CharacterImageFanoutRequestDTO {

    @Schema(
            description = "생성 범위. 허용값: MAIN_ONLY, GRAPH_VISIBLE, SELECTED, STALE_ONLY, FAILED_ONLY, ALL",
            example = "MAIN_ONLY",
            allowableValues = {"MAIN_ONLY", "GRAPH_VISIBLE", "SELECTED", "STALE_ONLY", "FAILED_ONLY", "ALL"}
    )
    private String scope;

    @Schema(description = "scope=SELECTED일 때 생성할 인물 DB ID 목록", example = "[10, 11, 12]", nullable = true)
    private List<Long> characterIds;

    @Schema(description = "이번 요청에서 최대 생성할 인물 수. 생략하면 제한하지 않습니다.", example = "10", nullable = true)
    private Integer limit;

    @Schema(description = "이미 게시 이미지가 있는 캐릭터도 후보를 새로 만들지 여부입니다.", example = "false", defaultValue = "false")
    @Builder.Default
    private Boolean overwritePublished = false;

    @Schema(
            description = "QA 통과 후 게시 정책. 허용값: AUTO_AFTER_QA, MANUAL",
            example = "MANUAL",
            allowableValues = {"AUTO_AFTER_QA", "MANUAL"}
    )
    @Builder.Default
    private String publishPolicy = "MANUAL";
}
