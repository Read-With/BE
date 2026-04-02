package com.kw.readwith.dto.admin;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@Schema(description = "인물 업로드 payload 루트")
public class CharacterListDTO {

    @JsonAlias("characters")
    @Schema(description = "인물 목록")
    private List<CharacterDTO> items;
}
