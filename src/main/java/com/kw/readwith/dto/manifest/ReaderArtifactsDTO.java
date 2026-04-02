package com.kw.readwith.dto.manifest;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(description = "리더가 직접 참조하는 정규화 산출물 정보")
public class ReaderArtifactsDTO {

    @Schema(description = "combined.xhtml 공개 경로", nullable = true)
    private String combinedXhtmlPath;

    @Schema(description = "리더에서 사용할 data-* 속성 목록")
    private List<String> dataAttributes;
}
