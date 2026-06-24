package com.kw.readwith.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(description = "도서 단위 캐릭터 이미지 생성/승인 현황")
public class CharacterImageBookStatusResponseDTO {

    @Schema(description = "도서 ID", example = "1")
    private Long bookId;

    @Schema(
            description = "대표 이미지 상태",
            allowableValues = {"NONE", "CANDIDATE_GENERATING", "QA_FAILED", "QA_PASSED", "APPROVED", "REJECTED", "SUPERSEDED"}
    )
    private String referenceStatus;

    @Schema(description = "활성 대표 이미지 asset ID", nullable = true)
    private Long activeReferenceAssetId;

    @Schema(description = "대표 캐릭터 DB ID", nullable = true)
    private Long referenceCharacterId;

    @Schema(description = "대표 이미지 버전", example = "1")
    private Integer referenceVersion;

    @Schema(description = "fan-out 생성 가능 여부")
    private boolean canFanout;

    @Schema(description = "캐릭터별 이미지 상태")
    private List<CharacterImageCharacterStatusDTO> characters;
}
