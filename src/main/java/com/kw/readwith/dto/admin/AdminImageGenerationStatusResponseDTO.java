package com.kw.readwith.dto.admin;

import com.kw.readwith.domain.Character;
import com.kw.readwith.domain.CharacterImageAsset;
import com.kw.readwith.service.CdnUrlService;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(
        description = """
                관리자 책 단위 이미지 생성 콘솔 상태 응답입니다.
                대표 캐릭터 후보사진 4장과 캐릭터별 현재 이미지 상태를 한 번에 내려줍니다.
                관리자 페이지는 이 응답의 status와 nextAction만 보고 다음 버튼을 결정할 수 있습니다.
                """
)
public class AdminImageGenerationStatusResponseDTO {

    @Schema(description = "책 DB ID", example = "1")
    private Long bookId;

    @Schema(
            description = """
                    책 단위 이미지 생성 상태입니다.
                    EMPTY: 아직 후보사진이 없습니다. REFERENCE_GENERATING: 대표 후보사진 생성 중입니다.
                    REFERENCE_READY: 후보사진 4장 중 선택 대기 상태입니다. FANOUT_GENERATING: 선택된 대표 후보로 나머지 캐릭터 생성 중입니다.
                    READY: 관리자 검토 가능한 상태입니다. FAILED: 후보 또는 캐릭터 이미지 중 실패 항목이 있습니다.
                    """,
            allowableValues = {"EMPTY", "REFERENCE_GENERATING", "REFERENCE_READY", "FANOUT_GENERATING", "READY", "FAILED"},
            example = "REFERENCE_READY"
    )
    private String status;

    @Schema(
            description = """
                    관리자 화면에서 다음으로 노출할 권장 액션입니다.
                    GENERATE_REFERENCE_CANDIDATES, WAIT_REFERENCE_CANDIDATES, SELECT_REFERENCE_CANDIDATE,
                    WAIT_FANOUT, REVIEW_OR_REGENERATE_CHARACTER_IMAGES 중 하나입니다.
                    """,
            example = "SELECT_REFERENCE_CANDIDATE"
    )
    private String nextAction;

    @Schema(description = "서버가 자동 지정한 대표 캐릭터입니다. 후보사진 4장은 이 캐릭터 기준으로 생성됩니다.", nullable = true)
    private CharacterSummary referenceCharacter;

    @Schema(description = "관리자가 선택한 대표 후보사진 asset ID입니다. 선택 전에는 null입니다.", nullable = true, example = "102")
    private Long selectedReferenceCandidateId;

    @Schema(description = "책에 저장된 대표 캐릭터 후보사진 목록입니다. slotNo 1~4만 유지되며 재생성 시 같은 slot을 덮어씁니다.")
    private List<ReferenceCandidate> referenceCandidates;

    @Schema(description = "책에 등록된 캐릭터별 현재 이미지 생성 상태 목록입니다.")
    private List<CharacterImage> characters;

    @Getter
    @Builder
    @Schema(description = "대표 캐릭터 요약")
    public static class CharacterSummary {
        @Schema(description = "캐릭터 DB ID", example = "10")
        private Long id;

        @Schema(description = "책 내부 characterId", example = "1")
        private Long bookCharacterId;

        @Schema(description = "캐릭터 이름", example = "Elizabeth Bennet")
        private String name;

        @Schema(description = "주요 캐릭터 여부", example = "true")
        private boolean mainCharacter;

        public static CharacterSummary from(Character character) {
            if (character == null) {
                return null;
            }
            return CharacterSummary.builder()
                    .id(character.getId())
                    .bookCharacterId(character.getCharacterId())
                    .name(character.getName())
                    .mainCharacter(character.isMainCharacter())
                    .build();
        }
    }

    @Getter
    @Builder
    @Schema(description = "대표 캐릭터 후보사진")
    public static class ReferenceCandidate {
        @Schema(description = "후보사진 asset ID", example = "101")
        private Long id;

        @Schema(description = "후보 슬롯 번호입니다. 책마다 1~4만 유지됩니다.", example = "1")
        private Integer slotNo;

        @Schema(
                description = """
                        관리자 화면 노출 상태입니다.
                        GENERATING: 생성 중입니다. READY: 선택 가능합니다.
                        SELECTED: 현재 active reference image입니다. FAILED: 생성에 실패했습니다.
                        """,
                allowableValues = {"GENERATING", "READY", "SELECTED", "FAILED"},
                example = "READY"
        )
        private String status;

        @Schema(description = "후보사진 이미지 URL입니다. 실패 또는 생성 중이면 null일 수 있습니다.", nullable = true)
        private String imageUrl;

        @Schema(description = "실패 코드입니다. 실패가 아니면 null입니다.", nullable = true, example = "REFERENCE_GENERATION_FAILED")
        private String failureCode;

        public static ReferenceCandidate from(CharacterImageAsset asset, Long selectedReferenceCandidateId) {
            return from(asset, selectedReferenceCandidateId, null);
        }

        public static ReferenceCandidate from(CharacterImageAsset asset,
                                              Long selectedReferenceCandidateId,
                                              CdnUrlService cdnUrlService) {
            return ReferenceCandidate.builder()
                    .id(asset.getId())
                    .slotNo(asset.getSlotNo())
                    .status(toReferenceStatus(asset, selectedReferenceCandidateId))
                    .imageUrl(toPublicUrl(cdnUrlService, asset.getS3Url()))
                    .failureCode(asset.getFailureCode())
                    .build();
        }

        private static String toReferenceStatus(CharacterImageAsset asset, Long selectedReferenceCandidateId) {
            if (asset.getId() != null && asset.getId().equals(selectedReferenceCandidateId)) {
                return "SELECTED";
            }
            return switch (asset.getStatus()) {
                case GENERATING, QA_PENDING -> "GENERATING";
                case FAILED, QA_FAILED -> "FAILED";
                default -> "READY";
            };
        }
    }

    @Getter
    @Builder
    @Schema(description = "캐릭터 이미지 상태")
    public static class CharacterImage {
        @Schema(description = "캐릭터 DB ID", example = "11")
        private Long id;

        @Schema(description = "책 내부 characterId", example = "2")
        private Long bookCharacterId;

        @Schema(description = "캐릭터 이름", example = "Mr. Darcy")
        private String name;

        @Schema(description = "주요 캐릭터 여부", example = "true")
        private boolean mainCharacter;

        @Schema(
                description = """
                        관리자 화면 노출 상태입니다.
                        EMPTY: 생성된 이미지가 없습니다. GENERATING: 생성 중입니다.
                        READY: 이미지 URL을 검토할 수 있습니다. FAILED: 재생성이 필요한 실패 상태입니다.
                        """,
                allowableValues = {"EMPTY", "GENERATING", "READY", "FAILED"},
                example = "READY"
        )
        private String imageStatus;

        @Schema(description = "현재 게시 또는 생성된 캐릭터 이미지 URL입니다.", nullable = true)
        private String imageUrl;

        @Schema(description = "캐릭터 이미지 asset ID입니다. 아직 생성된 이미지가 없으면 null입니다.", nullable = true, example = "120")
        private Long assetId;

        @Schema(description = "실패 코드입니다. 실패가 아니면 null입니다.", nullable = true, example = "REFERENCE_EDIT_FAILED")
        private String failureCode;

        @Schema(description = "대표 후보사진 기준 캐릭터인지 여부입니다.", example = "false")
        private boolean referenceCharacter;

        public static CharacterImage from(Character character,
                                          CharacterImageAsset asset,
                                          Long referenceCharacterId) {
            return from(character, asset, referenceCharacterId, null);
        }

        public static CharacterImage from(Character character,
                                          CharacterImageAsset asset,
                                          Long referenceCharacterId,
                                          CdnUrlService cdnUrlService) {
            return CharacterImage.builder()
                    .id(character.getId())
                    .bookCharacterId(character.getCharacterId())
                    .name(character.getName())
                    .mainCharacter(character.isMainCharacter())
                    .imageStatus(toCharacterStatus(asset, character))
                    .imageUrl(toPublicUrl(cdnUrlService, resolveImageUrl(asset, character)))
                    .assetId(asset != null ? asset.getId() : null)
                    .failureCode(asset != null ? asset.getFailureCode() : null)
                    .referenceCharacter(referenceCharacterId != null && referenceCharacterId.equals(character.getId()))
                    .build();
        }

        private static String toCharacterStatus(CharacterImageAsset asset, Character character) {
            if (asset == null) {
                return character.getProfileImage() == null || character.getProfileImage().isBlank() ? "EMPTY" : "READY";
            }
            return switch (asset.getStatus()) {
                case GENERATING, QA_PENDING -> "GENERATING";
                case FAILED, QA_FAILED -> "FAILED";
                default -> "READY";
            };
        }

        private static String resolveImageUrl(CharacterImageAsset asset, Character character) {
            if (asset != null && asset.getS3Url() != null && !asset.getS3Url().isBlank()) {
                return asset.getS3Url();
            }
            return character.getProfileImage();
        }
    }

    private static String toPublicUrl(CdnUrlService cdnUrlService, String value) {
        return cdnUrlService == null ? value : cdnUrlService.toPublicUrl(value);
    }
}
