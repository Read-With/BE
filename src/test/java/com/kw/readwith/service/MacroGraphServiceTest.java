package com.kw.readwith.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.Character;
import com.kw.readwith.domain.Chapter;
import com.kw.readwith.domain.Event;
import com.kw.readwith.domain.enums.AnalysisStatus;
import com.kw.readwith.domain.enums.NormalizationStatus;
import com.kw.readwith.domain.mapping.EventCharacterStat;
import com.kw.readwith.dto.common.LocatorDTO;
import com.kw.readwith.dto.graph.MacroGraphResponseDTO;
import com.kw.readwith.repository.BookRepository;
import com.kw.readwith.repository.ChapterRepository;
import com.kw.readwith.repository.EventCharacterStatRepository;
import com.kw.readwith.repository.EventRelationshipEdgeRepository;
import com.kw.readwith.repository.EventRepository;
import com.kw.readwith.util.LocatorSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MacroGraphServiceTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private ChapterRepository chapterRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventRelationshipEdgeRepository eventRelationshipEdgeRepository;

    @Mock
    private EventCharacterStatRepository eventCharacterStatRepository;

    @Mock
    private BookAccessPolicy bookAccessPolicy;

    @Mock
    private LocatorSupport locatorSupport;

    private MacroGraphService macroGraphService;

    @BeforeEach
    void setUp() {
        macroGraphService = new MacroGraphService(
                bookRepository,
                chapterRepository,
                eventRepository,
                eventRelationshipEdgeRepository,
                eventCharacterStatRepository,
                new ObjectMapper(),
                bookAccessPolicy,
                locatorSupport
        );
    }

    @Test
    @DisplayName("macro graph uptoLocator keeps the latest event before the locator in the current chapter")
    void getMacroGraphUsesUptoLocator() {
        Book book = sampleBook();
        Chapter chapter2 = sampleChapter(book, 2);
        Event earlierEvent = sampleEvent(book, chapter2, 1, "ch2-e1", 0, 40);
        Event laterEvent = sampleEvent(book, chapter2, 2, "ch2-e2", 50, 100);
        Character hero = sampleCharacter(book, 101L, 1001L, "Hero");
        EventCharacterStat stat = EventCharacterStat.builder()
                .event(earlierEvent)
                .character(hero)
                .nodeWeight(0.9)
                .build();
        LocatorDTO uptoLocator = LocatorDTO.builder()
                .chapterIndex(2)
                .blockIndex(1)
                .offset(5)
                .build();

        when(bookRepository.findById(10L)).thenReturn(Optional.of(book));
        when(chapterRepository.findByBookIdAndIdx(10L, 2)).thenReturn(Optional.of(chapter2));
        when(locatorSupport.toTxtOffset(chapter2, uptoLocator)).thenReturn(45);
        when(eventRepository.findByBookOrderByChapterIdxAscIdxAsc(book)).thenReturn(List.of(earlierEvent, laterEvent));
        when(eventRelationshipEdgeRepository.findByEventIn(List.of(earlierEvent))).thenReturn(List.of());
        when(eventCharacterStatRepository.findByEventIn(List.of(earlierEvent))).thenReturn(List.of(stat));

        MacroGraphResponseDTO response = macroGraphService.getMacroGraph(10L, null, uptoLocator, 1L);

        assertThat(response.getUserCurrentChapter()).isEqualTo(2);
        assertThat(response.getNodes()).hasSize(1);
        assertThat(response.getNodes().get(0).getId()).isEqualTo(1001L);
        assertThat(response.getNodes().get(0).getWeight()).isEqualTo(0.9f);
        assertThat(response.getNodes().get(0).getCount()).isEqualTo(1);
        assertThat(response.getEdges()).isEmpty();
    }

    private Book sampleBook() {
        return Book.builder()
                .id(10L)
                .title("Book")
                .author("Author")
                .language("en")
                .normalizationStatus(NormalizationStatus.READY)
                .analysisStatus(AnalysisStatus.READY)
                .build();
    }

    private Chapter sampleChapter(Book book, int idx) {
        return Chapter.builder()
                .id((long) idx)
                .book(book)
                .idx(idx)
                .build();
    }

    private Event sampleEvent(Book book, Chapter chapter, int idx, String eventId, int start, int end) {
        return Event.builder()
                .id((long) idx)
                .book(book)
                .chapter(chapter)
                .idx(idx)
                .eventId(eventId)
                .startTxtOffset(start)
                .endTxtOffset(end)
                .startBlockIndex(0)
                .startOffset(start)
                .endBlockIndex(0)
                .endOffset(end)
                .rawText("event")
                .build();
    }

    private Character sampleCharacter(Book book, Long id, Long characterId, String name) {
        return Character.builder()
                .id(id)
                .characterId(characterId)
                .book(book)
                .name(name)
                .isMainCharacter(true)
                .build();
    }
}
