package com.kw.readwith.dto.manifest;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CharacterManifestDTO {
    
    /**
     * 인물 ID (책 내 고유 ID)
     */
    private Long id;
    
    /**
     * 인물 이름
     */
    private String name;
    
    /**
     * 인물이 불리는 다른 이름들
     */
    private String names;
    
    /**
     * 프로필 이미지 URL
     */
    private String profileImage;
    
    /**
     * 주요 인물 여부
     */
    private Boolean isMainCharacter;
    
    /**
     * 첫 등장 챕터
     */
    private Integer firstChapterIdx;
    
    /**
     * 성격/인물 설명
     */
    private String personalityText;
    
    /**
     * 프로필 묘사
     */
    private String profileText;
}
