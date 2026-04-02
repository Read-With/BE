package com.kw.readwith.service;

import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.Chapter;
import com.kw.readwith.domain.Character;
import com.kw.readwith.domain.enums.AnalysisStatus;
import com.kw.readwith.dto.admin.CharacterPovSummaryDTO;
import com.kw.readwith.dto.book.ChapterPovSummaryResponseDTO;
import com.kw.readwith.repository.BookRepository;
import com.kw.readwith.repository.ChapterRepository;
import com.kw.readwith.repository.CharacterPovSummaryRepository;
import com.kw.readwith.repository.CharacterRepository;
import com.kw.readwith.domain.mapping.CharacterPovSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CharacterPovSummaryServiceTest {

    @Mock
    private CharacterPovSummaryRepository characterPovSummaryRepository;

    @Mock
    private ChapterRepository chapterRepository;

    @Mock
    private CharacterRepository characterRepository;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private BookAccessPolicy bookAccessPolicy;

    @InjectMocks
    private CharacterPovSummaryService characterPovSummaryService;

    @Test
    @DisplayName("POV summary 조회는 DB PK가 아니라 책 내부 characterId를 반환한다")
    void getChapterPovSummariesUsesLogicalCharacterId() {
        Book book = Book.builder()
                .id(1L)
                .analysisStatus(AnalysisStatus.READY)
                .build();
        Chapter chapter = Chapter.builder()
                .id(10L)
                .book(book)
                .idx(2)
                .title("chapter 2")
                .build();
        Character character = Character.builder()
                .id(99L)
                .book(book)
                .characterId(7L)
                .name("Alice")
                .isMainCharacter(true)
                .build();
        CharacterPovSummary summary = CharacterPovSummary.builder()
                .id(100L)
                .book(book)
                .chapter(chapter)
                .character(character)
                .summaryText("summary")
                .build();

        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        when(chapterRepository.findByBookIdAndIdx(1L, 2)).thenReturn(Optional.of(chapter));
        when(characterPovSummaryRepository.findByBookIdAndChapterIdx(1L, 2)).thenReturn(List.of(summary));
        doNothing().when(bookAccessPolicy).ensureReadable(book, 55L);

        ChapterPovSummaryResponseDTO response = characterPovSummaryService.getChapterPovSummaries(1L, 2, 55L);

        assertThat(response.getPovSummaries()).hasSize(1);
        assertThat(response.getPovSummaries().get(0).getCharacterId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("관리자 POV summary 조회도 책 내부 characterId를 반환한다")
    void getCharacterPovSummariesByChapterUsesLogicalCharacterId() {
        Book book = Book.builder().id(1L).build();
        Chapter chapter = Chapter.builder().id(10L).book(book).idx(1).build();
        Character character = Character.builder()
                .id(42L)
                .book(book)
                .characterId(5L)
                .name("Bob")
                .build();
        CharacterPovSummary summary = CharacterPovSummary.builder()
                .id(77L)
                .book(book)
                .chapter(chapter)
                .character(character)
                .summaryText("admin summary")
                .build();

        when(chapterRepository.existsById(10L)).thenReturn(true);
        when(characterPovSummaryRepository.findByChapterId(10L)).thenReturn(List.of(summary));

        List<CharacterPovSummaryDTO> response = characterPovSummaryService.getCharacterPovSummariesByChapter(10L);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getCharacterId()).isEqualTo(5L);
    }
}
