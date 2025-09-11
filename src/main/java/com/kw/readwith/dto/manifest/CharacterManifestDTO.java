package com.kw.readwith.dto.manifest;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

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
    @JsonProperty("common_name")
    private String name;
    
    /**
     * 인물이 불리는 다른 이름들
     */
    private List<String> names;
    
    /**
     * 프로필 이미지 URL
     */
    private String profileImage;
    
    /**
     * 주요 인물 여부
     */
    @JsonProperty("main_character")
    private Boolean isMainCharacter;
    
    /**
     * 첫 등장 챕터
     */
    private Integer firstChapterIdx;
    
    /**
     * 성격/인물 설명
     */
    @JsonProperty("portrait_prompt")
    private String personalityText;
    
    /**
     * 프로필 묘사
     */
    private String description;
}
