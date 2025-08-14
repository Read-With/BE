package com.kw.readwith.web.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class CharacterListDto {
    private List<CharacterDto> characters;
}
