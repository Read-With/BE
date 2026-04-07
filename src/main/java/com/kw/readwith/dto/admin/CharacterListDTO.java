package com.kw.readwith.dto.admin;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@Schema(description = "Character upload payload root")
public class CharacterListDTO {

    @JsonProperty("bookPrompt")
    @JsonAlias("book_prompt")
    @Schema(description = "Book-level common style prompt", nullable = true)
    private String bookPrompt;

    @JsonAlias("characters")
    @Schema(description = "Character list")
    private List<CharacterDTO> items;
}
