package com.kw.readwith.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class CharacterDTO {

    private Long id;

    @JsonProperty("common_name")
    private String commonName;

    private List<String> names;

    @JsonProperty("main_character")
    private boolean isMainCharacter;

    private String description;

    @JsonProperty("portrait_prompt")
    private String portraitPrompt;
}
