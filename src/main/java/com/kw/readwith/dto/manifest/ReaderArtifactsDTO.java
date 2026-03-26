package com.kw.readwith.dto.manifest;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ReaderArtifactsDTO {

    private String combinedXhtmlPath;
    private List<String> dataAttributes;
}
