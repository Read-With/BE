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
@Schema(description = "특정 캐릭터 이미지 후보 생성 요청")
public class CharacterImageCandidateRequestDTO {

    @Schema(description = "생성 직후 자동 QA를 실행할지 여부입니다.", example = "true", defaultValue = "true")
    @Builder.Default
    private Boolean autoQa = true;

    @Schema(
            description = "QA 통과 후 게시 정책. 허용값: AUTO_AFTER_QA, MANUAL",
            example = "MANUAL",
            allowableValues = {"AUTO_AFTER_QA", "MANUAL"}
    )
    @Builder.Default
    private String publishPolicy = "MANUAL";
}
