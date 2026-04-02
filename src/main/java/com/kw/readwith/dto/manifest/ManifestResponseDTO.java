package com.kw.readwith.dto.manifest;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(description = "리더 초기 진입에 필요한 전체 매니페스트 응답")
public class ManifestResponseDTO {

    @Schema(description = "도서 기본 메타데이터")
    private BookManifestDTO book;

    @Schema(description = "챕터 목록과 각 챕터에 속한 이벤트 목록")
    private List<ChapterManifestDTO> chapters;

    @Schema(description = "분석 완료 시 제공되는 인물 목록")
    private List<CharacterManifestDTO> characters;

    @Schema(description = "리더가 직접 참조하는 정규화 산출물 정보")
    private ReaderArtifactsDTO readerArtifacts;

    @Schema(description = "진도 계산에 필요한 메타데이터")
    private ProgressMetadataDTO progressMetadata;
}
