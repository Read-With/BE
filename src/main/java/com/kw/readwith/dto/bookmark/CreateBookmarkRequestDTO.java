package com.kw.readwith.dto.bookmark;

import com.kw.readwith.dto.common.LocatorDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "북마크 생성 요청")
public class CreateBookmarkRequestDTO {

    @NotNull(message = "책 ID는 필수입니다.")
    @Schema(description = "북마크를 생성할 도서 ID", example = "42")
    private Long bookId;

    @NotNull(message = "시작 locator는 필수입니다.")
    @Schema(description = "북마크 시작 위치")
    private LocatorDTO startLocator;

    @Schema(description = "범위 북마크일 때의 종료 위치. 단일 포인트 북마크면 생략합니다.", nullable = true)
    private LocatorDTO endLocator;

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "색상은 유효한 HEX 코드여야 합니다. (예: #ffd700)")
    @Schema(description = "북마크 색상(HEX)", example = "#FFD700", nullable = true)
    private String color;

    @Size(max = 1000, message = "메모는 1000자를 초과할 수 없습니다.")
    @Schema(description = "북마크 메모", example = "여기서 사건이 본격적으로 시작됨", nullable = true)
    private String memo;
}
