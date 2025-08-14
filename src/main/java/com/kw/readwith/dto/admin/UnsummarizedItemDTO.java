package com.kw.readwith.dto.admin;

import com.kw.readwith.domain.Chapter;
import com.kw.readwith.domain.Character;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class UnsummarizedItemDTO {
    private Long id;
    private String name; // Chapter title or Character name
    private Long bookId;
    private String bookTitle;

    public static UnsummarizedItemDTO from(Chapter chapter) {
        return new UnsummarizedItemDTO(chapter.getId(), chapter.getTitle(), chapter.getBook().getId(), chapter.getBook().getTitle());
    }

    public static UnsummarizedItemDTO from(Character character) {
        return new UnsummarizedItemDTO(character.getId(), character.getName(), character.getBook().getId(), character.getBook().getTitle());
    }
}