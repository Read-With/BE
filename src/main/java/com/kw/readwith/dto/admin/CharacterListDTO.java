package com.kw.readwith.dto.admin;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class CharacterListDTO {

    @JsonAlias("characters")
    private List<CharacterDTO> items;
}
