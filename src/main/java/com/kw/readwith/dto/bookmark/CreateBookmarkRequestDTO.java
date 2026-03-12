package com.kw.readwith.dto.bookmark;

import com.kw.readwith.dto.common.LocatorDTO;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CreateBookmarkRequestDTO {

    @NotNull(message = "책 ID는 필수입니다.")
    private Long bookId;

    @NotNull(message = "시작 locator는 필수입니다.")
    private LocatorDTO startLocator;

    private LocatorDTO endLocator;

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "색상은 유효한 HEX 코드여야 합니다. (예: #ffd700)")
    private String color;

    @Size(max = 1000, message = "메모는 1000자를 초과할 수 없습니다.")
    private String memo;
}
