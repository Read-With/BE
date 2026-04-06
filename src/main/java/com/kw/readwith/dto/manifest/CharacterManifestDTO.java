package com.kw.readwith.dto.manifest;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "매니페스트 안의 인물 정보")
public class CharacterManifestDTO {

    @Schema(description = "책 내부 characterId", example = "7")
    private Long id;

    @Schema(description = "대표 이름")
    private String name;

    @Schema(description = "이름/별칭 묶음 문자열", nullable = true)
    private String names;

    @Schema(description = "프로필 이미지 URL", nullable = true)
    private String profileImage;

    @Schema(description = "주요 인물 여부")
    @Getter(AccessLevel.NONE)
    private Boolean isMainCharacter;

    @Schema(description = "처음 등장한 챕터 인덱스", nullable = true)
    private Integer firstChapterIdx;

    @Schema(description = "인물 설명", nullable = true)
    private String personalityText;

    @Schema(description = "이미지 생성용 프롬프트 또는 프로필 텍스트", nullable = true)
    private String profileText;

    @JsonProperty("isMainCharacter")
    public Boolean getIsMainCharacter() {
        return isMainCharacter;
    }
}
