package com.kw.readwith.web.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class CharacterDto {
    private String common_name;
    private String portrait_prompt;
    private boolean main_character;
    private String description;
    private Double id;
    private List<String> names;
}
