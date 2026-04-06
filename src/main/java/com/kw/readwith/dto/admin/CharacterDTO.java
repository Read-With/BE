package com.kw.readwith.dto.admin;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "인물 1명에 대한 업로드 및 응답 항목")
public class CharacterDTO {

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Schema(description = "인물 DB 고유 ID (PK)", accessMode = Schema.AccessMode.READ_ONLY)
    private Long id;

    @JsonAlias("id")
    @Schema(description = "책 내부 characterId. 숫자 문자열을 사용합니다.", example = "7")
    private String characterId;

    @JsonAlias("common_name")
    @Schema(description = "대표 이름", example = "셜록 홈즈")
    private String commonName;

    @Schema(description = "이름/별칭 목록")
    private List<String> names;

    @JsonAlias("main_character")
    @Schema(description = "주요 인물 여부")
    private boolean isMainCharacter;

    @Schema(description = "언어별 설명. 현재는 `ko`를 사용합니다.")
    private Map<String, String> descriptions;

    @JsonProperty("description_ko")
    @Schema(description = "legacy 한국어 설명 필드", deprecated = true, nullable = true)
    private String legacyDescriptionKo;

    @JsonAlias("portrait_prompt")
    @Schema(description = "이미지 생성용 프롬프트 또는 프로필 텍스트")
    private String portraitPrompt;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Schema(description = "프로필 이미지 URL", accessMode = Schema.AccessMode.READ_ONLY)
    private String profileImage;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Schema(description = "이미지 생성 상태", accessMode = Schema.AccessMode.READ_ONLY)
    private String imageGenerationStatus;
}
