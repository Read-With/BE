package com.kw.readwith.dto.admin;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class CharacterListDTO {
    private List<CharacterDTO> characters;
}
