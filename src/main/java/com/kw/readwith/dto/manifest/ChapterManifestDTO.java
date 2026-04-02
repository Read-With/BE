package com.kw.readwith.dto.manifest;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(description = "매니페스트 안의 챕터 정보")
public class ChapterManifestDTO {

    @Schema(description = "챕터 인덱스(1-based)", example = "3")
    private Integer idx;

    @Schema(description = "챕터 제목")
    private String title;

    @Schema(description = "EPUB spine href", nullable = true)
    private String spineHref;

    @Schema(description = "문단 수", nullable = true)
    private Integer paragraphCount;

    @Schema(description = "문단 시작 위치 배열(JSON 문자열)", nullable = true)
    private String paragraphStartsJson;

    @Schema(description = "문단 길이 배열(JSON 문자열)", nullable = true)
    private String paragraphLengthsJson;

    @Schema(description = "챕터 전체 code point 수", nullable = true)
    private Integer totalCodePoints;

    @Schema(description = "본문 전체 기준 시작 위치", nullable = true)
    private Integer startPos;

    @Schema(description = "본문 전체 기준 종료 위치", nullable = true)
    private Integer endPos;

    @Schema(description = "챕터 원문 일부", nullable = true)
    private String rawText;

    @Schema(description = "챕터 요약 텍스트", nullable = true)
    private String summaryText;

    @Schema(description = "요약 업로드 URL", nullable = true)
    private String summaryUploadUrl;

    @Schema(description = "POV summary 캐시 여부")
    private Boolean povSummariesCached;

    @Schema(description = "챕터에 속한 이벤트 목록")
    private List<EventManifestDTO> events;
}
