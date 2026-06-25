package com.kw.readwith.dto.admin;

import com.kw.readwith.domain.Character;
import com.kw.readwith.domain.CharacterImageAsset;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "캐릭터별 이미지 게시/후보 상태")
public class CharacterImageCharacterStatusDTO {

    @Schema(description = "인물 DB ID", example = "10")
    private Long id;

    @Schema(description = "책 내부 characterId", example = "1")
    private Long bookCharacterId;

    @Schema(description = "인물 이름", example = "Elizabeth Bennet")
    private String name;

    @Schema(description = "주요 인물 여부")
    private boolean mainCharacter;

    @Schema(description = "현재 게시 이미지 URL", nullable = true)
    private String publishedImageUrl;

    @Schema(description = "기존 이미지 생성 상태", nullable = true)
    private String imageGenerationStatus;

    @Schema(description = "최신 이미지 asset", nullable = true)
    private CharacterImageAssetDTO latestAsset;

    public static CharacterImageCharacterStatusDTO from(Character character, CharacterImageAsset latestAsset) {
        return CharacterImageCharacterStatusDTO.builder()
                .id(character.getId())
                .bookCharacterId(character.getCharacterId())
                .name(character.getName())
                .mainCharacter(character.isMainCharacter())
                .publishedImageUrl(character.getProfileImage())
                .imageGenerationStatus(character.getImageGenerationStatus() != null ? character.getImageGenerationStatus().name() : null)
                .latestAsset(CharacterImageAssetDTO.from(latestAsset))
                .build();
    }
}
