package com.kw.readwith.dto.admin;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class CharacterDTO {
    private String common_name;
    private String portrait_prompt;
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
