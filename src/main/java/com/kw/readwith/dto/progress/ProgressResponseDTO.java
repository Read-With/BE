package com.kw.readwith.dto.progress;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kw.readwith.dto.common.LocatorDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgressResponseDTO {

    private Long bookId;

    private LocatorDTO startLocator;

    private LocatorDTO endLocator;

    private Integer startTxtOffset;

    private Integer endTxtOffset;

    private String locatorVersion;

    private LocalDateTime updatedAt;

    @JsonProperty("locator")
    public LocatorDTO getLocator() {
        return startLocator;
    }
}
