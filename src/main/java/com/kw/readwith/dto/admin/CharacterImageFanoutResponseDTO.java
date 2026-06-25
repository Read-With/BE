package com.kw.readwith.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(description = "fan-out 이미지 생성 결과")
public class CharacterImageFanoutResponseDTO {

    @Schema(description = "요청 scope")
    private String scope;

    @Schema(description = "생성 대상 수", example = "10")
    private int targetCount;

    @Schema(description = "생성 성공 수", example = "8")
    private int successCount;

    @Schema(description = "실패 수", example = "1")
    private int failedCount;

    @Schema(description = "스킵 수", example = "1")
    private int skippedCount;

    @Schema(description = "생성된 asset 목록")
    private List<CharacterImageAssetDTO> assets;
}
