package com.kw.readwith.dto.manifest;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ManifestResponseDTO {
    
    /**
     * 책 기본 정보
     */
    private BookManifestDTO book;
    
    /**
     * 챕터 목록 (이벤트 포함)
     */
    private List<ChapterManifestDTO> chapters;
    
    /**
     * 인물 목록
     */
    private List<CharacterManifestDTO> characters;
}
