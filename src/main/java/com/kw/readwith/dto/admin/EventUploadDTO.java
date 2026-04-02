package com.kw.readwith.dto.admin;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class EventUploadDTO {

    private Integer chapterIndex;
    private List<EventDTO> items;
}
