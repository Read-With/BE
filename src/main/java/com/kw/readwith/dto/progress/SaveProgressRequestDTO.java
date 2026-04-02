package com.kw.readwith.dto.progress;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.kw.readwith.dto.common.LocatorDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "독서 진도 저장 요청")
public class SaveProgressRequestDTO {

    @NotNull(message = "bookId is required.")
    @Schema(description = "진도를 저장할 도서 ID", example = "42")
    private Long bookId;

    @JsonAlias("locator")
    @NotNull(message = "startLocator is required.")
    @Schema(description = "현재 읽기 위치. 신규 클라이언트는 startLocator를 사용합니다.")
    private LocatorDTO startLocator;

    @Schema(name = "locator", description = "legacy 요청 필드 alias입니다. 신규 클라이언트는 startLocator를 사용하세요.", deprecated = true)
    public LocatorDTO getLocator() {
        return startLocator;
    }
}
