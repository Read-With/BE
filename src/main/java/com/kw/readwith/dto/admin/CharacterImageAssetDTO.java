package com.kw.readwith.dto.admin;

import com.kw.readwith.domain.CharacterImageAsset;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "캐릭터 이미지 후보/게시 asset 응답")
public class CharacterImageAssetDTO {

    @Schema(description = "이미지 asset ID", example = "100")
    private Long id;

    @Schema(description = "도서 ID", example = "1")
    private Long bookId;

    @Schema(description = "인물 DB ID", example = "10")
    private Long characterId;

    @Schema(description = "인물 이름", example = "Elizabeth Bennet")
    private String characterName;

    @Schema(
            description = "asset 역할",
            allowableValues = {"REFERENCE_SEED", "DERIVED_CHARACTER", "STANDALONE_TEXT"}
    )
    private String assetRole;

    @Schema(
            description = "생성 방식",
            allowableValues = {"TEXT_TO_IMAGE", "REFERENCE_EDIT"}
    )
    private String generationMode;

    @Schema(
            description = "asset 상태",
            allowableValues = {
                    "GENERATING",
                    "QA_PENDING",
                    "QA_PASSED",
                    "QA_FAILED",
                    "REVIEW_REQUIRED",
                    "APPROVED",
                    "REJECTED",
                    "PUBLISHED",
                    "FAILED",
                    "STALE_REFERENCE",
                    "STALE_PROMPT",
                    "SUPERSEDED"
            }
    )
    private String status;

    @Schema(description = "S3 공개 URL", nullable = true)
    private String s3Url;

    @Schema(description = "사용 모델", nullable = true, example = "gpt-image-1")
    private String model;

    @Schema(description = "참조 asset ID", nullable = true)
    private Long sourceReferenceAssetId;

    @Schema(description = "참조 버전", example = "1")
    private Integer referenceVersion;

    @Schema(description = "시도 횟수", example = "1")
    private Integer attemptNo;

    @Schema(description = "실패 코드", nullable = true)
    private String failureCode;

    @Schema(description = "생성 시각")
    private LocalDateTime createdAt;

    @Schema(description = "게시 시각", nullable = true)
    private LocalDateTime publishedAt;

    public static CharacterImageAssetDTO from(CharacterImageAsset asset) {
        if (asset == null) {
            return null;
        }

        return CharacterImageAssetDTO.builder()
                .id(asset.getId())
                .bookId(asset.getBook().getId())
                .characterId(asset.getCharacter().getId())
                .characterName(asset.getCharacter().getName())
                .assetRole(asset.getAssetRole().name())
                .generationMode(asset.getGenerationMode().name())
                .status(asset.getStatus().name())
                .s3Url(asset.getS3Url())
                .model(asset.getModel())
                .sourceReferenceAssetId(asset.getSourceReferenceAsset() != null ? asset.getSourceReferenceAsset().getId() : null)
                .referenceVersion(asset.getReferenceVersion())
                .attemptNo(asset.getAttemptNo())
                .failureCode(asset.getFailureCode())
                .createdAt(asset.getCreatedAt())
                .publishedAt(asset.getPublishedAt())
                .build();
    }
}
