package com.kw.readwith.dto.admin;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Getter
@NoArgsConstructor
public class CharacterDTO {

    @JsonAlias("id")
    private String characterId;

    @JsonAlias("common_name")
    private String commonName;

    private List<String> names;

    @JsonAlias("main_character")
    private boolean isMainCharacter;

    private Map<String, String> descriptions;

    @JsonProperty("description_ko")
    private String legacyDescriptionKo;

    @JsonAlias("portrait_prompt")
    private String portraitPrompt;
}
