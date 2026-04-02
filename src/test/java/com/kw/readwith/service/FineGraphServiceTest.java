package com.kw.readwith.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kw.readwith.config.V2TransitionGuard;
import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.Character;
import com.kw.readwith.domain.Chapter;
import com.kw.readwith.domain.Event;
import com.kw.readwith.domain.enums.AnalysisStatus;
import com.kw.readwith.domain.enums.NormalizationStatus;
import com.kw.readwith.domain.mapping.EventCharacterStat;
import com.kw.readwith.dto.common.LocatorDTO;
import com.kw.readwith.dto.graph.FineGraphResponseDTO;
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
class FineGraphServiceTest {

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
    private V2TransitionGuard transitionGuard;

    @Mock
    private BookAccessPolicy bookAccessPolicy;

    @Mock
    private LocatorSupport locatorSupport;

    private FineGraphService fineGraphService;

    @BeforeEach
    void setUp() {
        fineGraphService = new FineGraphService(
                bookRepository,
                chapterRepository,
                eventRepository,
                eventRelationshipEdgeRepository,
                eventCharacterStatRepository,
                new ObjectMapper(),
                transitionGuard,
                bookAccessPolicy,
                locatorSupport
        );
    }

    @Test
    @DisplayName("fine graph locator query resolves the matching event and returns stat-only nodes")
    void getFineGraphResolvesEventByLocator() {
        Book book = sampleBook();
        Chapter chapter = sampleChapter(book, 2);
        Event firstEvent = sampleEvent(book, chapter, 1, "ch2-e1", 0, 50);
        Event targetEvent = sampleEvent(book, chapter, 2, "ch2-e2", 50, 100);
        Character hero = sampleCharacter(book, 101L, 1001L, "Hero");
        EventCharacterStat heroStat = EventCharacterStat.builder()
                .event(targetEvent)
                .character(hero)
                .nodeWeight(0.75)
                .build();
        LocatorDTO locator = LocatorDTO.builder()
                .chapterIndex(2)
                .blockIndex(3)
                .offset(4)
                .build();

        when(bookRepository.findById(10L)).thenReturn(Optional.of(book));
        when(chapterRepository.findByBookIdAndIdx(10L, 2)).thenReturn(Optional.of(chapter));
        when(locatorSupport.toTxtOffset(chapter, locator)).thenReturn(60);
        when(eventRepository.findByChapterOrderByIdx(chapter)).thenReturn(List.of(firstEvent, targetEvent));
        when(eventRelationshipEdgeRepository.findByEvent(targetEvent)).thenReturn(List.of());
        when(eventCharacterStatRepository.findByEvent(targetEvent)).thenReturn(List.of(heroStat));

        FineGraphResponseDTO response = fineGraphService.getFineGraph(10L, null, null, locator, 1L);

        assertThat(response.getEventInfo()).isNotNull();
        assertThat(response.getEventInfo().getEventId()).isEqualTo("ch2-e2");
        assertThat(response.getNodes()).hasSize(1);
        assertThat(response.getNodes().get(0).getId()).isEqualTo(1001L);
        assertThat(response.getNodes().get(0).getWeight()).isEqualTo(0.75f);
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
