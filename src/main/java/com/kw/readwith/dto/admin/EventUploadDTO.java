package com.kw.readwith.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@Schema(description = "이벤트 업로드 payload 루트")
public class EventUploadDTO {

    @Schema(description = "파일이 담당하는 챕터 인덱스(1-based)", example = "3")
    private Integer chapterIndex;

    @Schema(description = "이벤트 목록")
    private List<EventDTO> items;
}
