package com.kw.readwith.dto.progress;

import com.kw.readwith.dto.common.LocatorDTO;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgressResponseDTO {

    private Long bookId;

    private LocatorDTO locator;

    private LocalDateTime updatedAt;

}
