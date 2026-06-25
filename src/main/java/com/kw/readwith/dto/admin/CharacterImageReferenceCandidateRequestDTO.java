package com.kw.readwith.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "책 단위 대표 이미지 후보 생성 요청")
public class CharacterImageReferenceCandidateRequestDTO {

    @Schema(
            description = "대표 이미지 후보로 사용할 인물 DB ID입니다. 생략하면 주요 인물 정책에 따라 자동 선정합니다.",
            example = "10",
            nullable = true
    )
    private Long characterId;

    @Schema(description = "생성 직후 자동 QA를 실행할지 여부입니다.", example = "true", defaultValue = "true")
    @Builder.Default
    private Boolean autoQa = true;
}
