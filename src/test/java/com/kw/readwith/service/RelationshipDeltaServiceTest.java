package com.kw.readwith.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.Chapter;
import com.kw.readwith.domain.Character;
import com.kw.readwith.domain.Event;
import com.kw.readwith.domain.enums.AnalysisStatus;
import com.kw.readwith.domain.enums.NormalizationStatus;
import com.kw.readwith.domain.mapping.EventCharacterStat;
import com.kw.readwith.domain.mapping.EventRelationshipEdge;
import com.kw.readwith.dto.relationship.RelationshipDeltaListResponseDTO;
import com.kw.readwith.dto.relationship.RelationshipGraphResponseDTO;
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
class RelationshipDeltaServiceTest {

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

    private RelationshipDeltaService relationshipDeltaService;

    @BeforeEach
    void setUp() {
        relationshipDeltaService = new RelationshipDeltaService(
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
    @DisplayName("raw delta query returns event delta payload shape")
    void getRelationshipDeltasReturnsRawDeltaShape() {
        Book book = sampleBook();
        Chapter chapter = sampleChapter(book, 1);
        Event event = sampleEvent(book, chapter, 1, "ch1-e1");
        Character nick = sampleCharacter(book, 101L, 1L, "Nick");
        Character gatsby = sampleCharacter(book, 102L, 2L, "Gatsby");
        EventRelationshipEdge edge = sampleEdge(event, nick, gatsby, 0.8f, 2, "[\"trust\"]", "reason");
        EventCharacterStat stat = sampleStat(event, nick, 0.5);

        when(bookRepository.findById(10L)).thenReturn(Optional.of(book));
        when(eventRepository.findByBookOrderByChapterIdxAscIdxAsc(book)).thenReturn(List.of(event));
        when(eventRelationshipEdgeRepository.findByEventsOrdered(List.of(event))).thenReturn(List.of(edge));
        when(eventCharacterStatRepository.findByEventsOrdered(List.of(event))).thenReturn(List.of(stat));

        RelationshipDeltaListResponseDTO response = relationshipDeltaService.getRelationshipDeltas(
                10L, null, "ch1-e1", null, null, 1L
        );

        assertThat(response.getDeltas()).hasSize(1);
        assertThat(response.getDeltas().get(0).getContractVersion()).isEqualTo("relationship-delta-v1");
        assertThat(response.getDeltas().get(0).getItems()).hasSize(1);
        assertThat(response.getDeltas().get(0).getItems().get(0).getReason()).isEqualTo("reason");
        assertThat(response.getDeltas().get(0).getNodeWeights()).containsKey("1");
    }

    @Test
    @DisplayName("relationship graph folds deltas by event order")
    void getRelationshipGraphFoldsDeltas() {
        Book book = sampleBook();
        Chapter chapter = sampleChapter(book, 1);
        Event event1 = sampleEvent(book, chapter, 1, "ch1-e1");
        Event event2 = sampleEvent(book, chapter, 2, "ch1-e2");
        Character nick = sampleCharacter(book, 101L, 1L, "Nick");
        Character gatsby = sampleCharacter(book, 102L, 2L, "Gatsby");
        EventRelationshipEdge edge1 = sampleEdge(event1, nick, gatsby, 1.0f, 1, "[\"trust\"]", "first");
        EventRelationshipEdge edge2 = sampleEdge(event2, gatsby, nick, -0.5f, 3, "[\"conflict\"]", "second");

        when(bookRepository.findById(10L)).thenReturn(Optional.of(book));
        when(eventRepository.findByBookOrderByChapterIdxAscIdxAsc(book)).thenReturn(List.of(event1, event2));
        when(eventRelationshipEdgeRepository.findByEventsOrdered(List.of(event1, event2))).thenReturn(List.of(edge1, edge2));
        when(eventCharacterStatRepository.findByEventsOrdered(List.of(event1, event2))).thenReturn(List.of());

        RelationshipGraphResponseDTO response = relationshipDeltaService.getRelationshipGraph(
                10L, "book", null, null, null, null, 1L
        );

        assertThat(response.getEdges()).hasSize(1);
        assertThat(response.getEdges().get(0).getEvidenceCount()).isEqualTo(4);
        assertThat(response.getEdges().get(0).getPositivity()).isEqualTo(-0.125);
        assertThat(response.getEdges().get(0).getLatestReason()).isEqualTo("second");
        assertThat(response.getEdges().get(0).getDirectionCounts()).containsEntry("1->2", 1);
        assertThat(response.getEdges().get(0).getDirectionCounts()).containsEntry("2->1", 3);
    }

    private Book sampleBook() {
        return Book.builder()
                .id(10L)
                .title("Book")
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

    private Event sampleEvent(Book book, Chapter chapter, int idx, String eventId) {
        return Event.builder()
                .id((long) idx)
                .book(book)
                .chapter(chapter)
                .idx(idx)
                .eventId(eventId)
                .startTxtOffset(idx * 10)
                .endTxtOffset(idx * 10 + 5)
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

    private EventRelationshipEdge sampleEdge(
            Event event,
            Character from,
            Character to,
            Float positivity,
            Integer evidenceCount,
            String labelsJson,
            String reason
    ) {
        return EventRelationshipEdge.builder()
                .event(event)
                .fromCharacter(from)
                .toCharacter(to)
                .sentimentScore(positivity)
                .interactionCount(evidenceCount)
                .relationTags(labelsJson)
                .explanation(reason)
                .build();
    }

    private EventCharacterStat sampleStat(Event event, Character character, double weight) {
        return EventCharacterStat.builder()
                .event(event)
                .character(character)
                .nodeWeight(weight)
                .build();
    }
}
