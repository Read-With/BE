package com.kw.readwith.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class CharacterDTO {
    @JsonProperty("common_name")
    private String common_name;
    
    @JsonProperty("portrait_prompt")
    private String portrait_prompt;
    
    @JsonProperty("main_character")
    private boolean main_character;
    
    private String description;
    
    private Double id;
    
    private List<String> names;

    @Getter
    @NoArgsConstructor
    public static class CharacterListDTO {
        private List<CharacterDTO> characters;
    }
}
