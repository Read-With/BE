package com.kw.readwith.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CharacterInfoDTO {

    @JsonProperty("id")
    private Double id;

    @JsonProperty("common_name")
    private String commonName;

    @JsonProperty("main_character")
    private boolean isMainCharacter;

    @JsonProperty("description")
    private String description;

    @JsonProperty("portrait_prompt")
    private String portraitPrompt;

    @JsonProperty("names")
    private List<String> names;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CharacterListDTO {
        private List<CharacterInfoDTO> characters;
    }
}
