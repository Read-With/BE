package com.kw.readwith.dto.progress;

import lombok.*;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgressResponseDTO {

    Integer chapterIdx;

    Integer eventIdx;

    String  cfi;

}