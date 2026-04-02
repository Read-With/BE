package com.kw.readwith.dto.progress;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.kw.readwith.dto.common.LocatorDTO;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveProgressRequestDTO {

    @NotNull(message = "bookId is required.")
    private Long bookId;

    @JsonAlias("locator")
    @NotNull(message = "startLocator is required.")
    private LocatorDTO startLocator;

    public LocatorDTO getLocator() {
        return startLocator;
    }
}
