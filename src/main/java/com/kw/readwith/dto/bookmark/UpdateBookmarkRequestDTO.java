package com.kw.readwith.dto.bookmark;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "북마크 수정 요청")
public class UpdateBookmarkRequestDTO {

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "색상은 유효한 HEX 코드여야 합니다. (예: #ffd700)")
    @Schema(description = "수정할 북마크 색상(HEX)", example = "#4A90E2", nullable = true)
    private String color;

    @Size(max = 1000, message = "메모는 1000자를 초과할 수 없습니다.")
    @Schema(description = "수정할 북마크 메모", example = "다시 읽을 부분", nullable = true)
    private String memo;
}
