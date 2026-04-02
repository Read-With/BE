package com.kw.readwith.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@Schema(description = "챕터 요약 업로드 payload 루트")
public class SummaryUploadDTO {

    @Schema(description = "파일이 담당하는 챕터 인덱스(1-based)", example = "3")
    private Integer chapterIndex;

    @Schema(description = "요약 언어 코드. 현재는 ko만 공식 입력입니다.", example = "ko")
    private String language;

    @Schema(description = "인물별 요약 목록. 비어 있으면 업로드가 거부됩니다.")
    private List<SummaryItemDTO> items;
}
