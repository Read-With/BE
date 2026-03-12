package com.kw.readwith.dto.progress;

import com.kw.readwith.dto.common.LocatorDTO;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveProgressRequestDTO {

    @NotNull(message = "책 ID는 필수입니다.")
    private Long bookId;

    @NotNull(message = "locator는 필수입니다.")
    private LocatorDTO locator;

}
