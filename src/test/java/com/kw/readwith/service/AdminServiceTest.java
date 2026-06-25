package com.kw.readwith.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.config.V2TransitionGuard;
import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.Chapter;
import com.kw.readwith.domain.Character;
import com.kw.readwith.domain.Event;
import com.kw.readwith.domain.mapping.EventCharacterStat;
import com.kw.readwith.domain.mapping.EventRelationshipEdge;
import com.kw.readwith.dto.admin.UnsummarizedItemDTO;
import com.kw.readwith.repository.BookRepository;
import com.kw.readwith.repository.CharacterPovSummaryRepository;
import com.kw.readwith.repository.CharacterRepository;
import com.kw.readwith.repository.ChapterRepository;
import com.kw.readwith.repository.EventCharacterStatRepository;
import com.kw.readwith.repository.EventRelationshipEdgeRepository;
import com.kw.readwith.repository.EventRepository;
import com.kw.readwith.service.normalization.NormalizationVersionService;
import com.kw.readwith.service.normalization.NormalizedArtifactStorageService;
import com.kw.readwith.util.LocatorSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @InjectMocks
    private AdminService adminService;

    @Mock
    private BookRepository bookRepository;
    @Mock
    private ChapterRepository chapterRepository;
    @Mock
    private CharacterRepository characterRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private EventRelationshipEdgeRepository eventRelationshipEdgeRepository;
    @Mock
    private EventCharacterStatRepository statRepository;
    @Mock
    private CharacterPovSummaryRepository characterPovSummaryRepository;
    @Mock
    private CharacterImageService characterImageService;
    @Mock
    private BookAnalysisStatusService bookAnalysisStatusService;
    @Mock
    private LocatorSupport locatorSupport;
    @Mock
    private V2TransitionGuard transitionGuard;
    @Mock
    private NormalizationVersionService normalizationVersionService;
    @Mock
    private NormalizedArtifactStorageService normalizedArtifactStorageService;
    @Mock
    private EntityManager entityManager;

    @Spy
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(adminService, "entityManager", entityManager);
    }

    @Test
    @DisplayName("getUnsummarizedChapters returns DTOs from repository chapters")
    void getUnsummarizedChapters() {
        Book book = Book.builder().id(1L).title("Test Book").build();
        Chapter chapter = Chapter.builder().id(10L).title("Test Chapter").book(book).build();

        given(chapterRepository.findUnsummarizedChapters()).willReturn(List.of(chapter));

        List<UnsummarizedItemDTO> result = adminService.getUnsummarizedChapters();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(10L);
        assertThat(result.get(0).getName()).isEqualTo("Test Chapter");
        assertThat(result.get(0).getBookTitle()).isEqualTo("Test Book");
        verify(chapterRepository, times(1)).findUnsummarizedChapters();
    }

    @Test
    @DisplayName("uploadCharacters saves parsed characters")
    void uploadCharacters_savesNewCharacters() throws IOException {
        Long bookId = 1L;
        String jsonContent = """
                {
                  "bookPrompt": "Victorian urban gothic, muted sepia and deep green palette",
                  "characters": [
                    {
                      "id": "1",
                      "common_name": "Harry Potter",
                      "names": ["The Boy Who Lived"],
                      "isMainCharacter": true,
                      "descriptions": {
                        "ko": "wizard"
                      },
                      "portrait_prompt": "portrait prompt"
                    }
                  ]
                }
                """;
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "characters.json",
                "application/json",
                jsonContent.getBytes(StandardCharsets.UTF_8)
        );

        Book book = Book.builder().id(bookId).build();
        given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
        given(characterRepository.findByBookAndCharacterId(book, 1L)).willReturn(Optional.empty());
        given(characterRepository.saveAll(anyList())).willAnswer(invocation -> invocation.getArgument(0));

        adminService.uploadCharacters(bookId, file);

        ArgumentCaptor<List<Character>> captor = ArgumentCaptor.forClass(List.class);
        verify(characterRepository, times(1)).saveAll(captor.capture());
        List<Character> savedCharacters = captor.getValue();
        assertThat(savedCharacters).hasSize(1);
        assertThat(savedCharacters.get(0).getName()).isEqualTo("Harry Potter");
        assertThat(savedCharacters.get(0).isMainCharacter()).isTrue();
        assertThat(book.getBookPrompt()).isEqualTo("Victorian urban gothic, muted sepia and deep green palette");
        verify(characterImageService, never()).generateImagesAsync(anyList());
    }

    @Test
    @DisplayName("uploadEvents validates against normalized chapter text and saves events")
    void uploadEvents_savesEvents() throws IOException {
        Long bookId = 1L;
        int chapterIdx = 1;
        String fileName = String.format("chapter%d_events.json", chapterIdx);
        String jsonContent = """
                {
                  "chapterIndex": 1,
                  "items": [
                    {
                      "event_id": "ch1-e1",
                      "startTxtOffset": 0,
                      "endTxtOffset": 6,
                      "eventText": "ABCDEF"
                    }
                  ]
                }
                """;
        MockMultipartFile file = new MockMultipartFile(
                "files",
                fileName,
                "application/json",
                jsonContent.getBytes(StandardCharsets.UTF_8)
        );

        Book book = Book.builder()
                .id(bookId)
                .normalizedArtifactPath("books/1/normalizations/run-1")
                .build();
        Chapter chapter = Chapter.builder()
                .id(10L)
                .idx(chapterIdx)
                .book(book)
                .rawText("preview only")
                .build();

        given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
        given(chapterRepository.findByBookIdAndIdx(bookId, chapterIdx)).willReturn(Optional.of(chapter));
        given(eventRepository.existsByChapter(chapter)).willReturn(false);
        given(eventRepository.saveAll(anyList())).willAnswer(invocation -> invocation.getArgument(0));
        given(normalizedArtifactStorageService.loadNormalizedChapterText("books/1/normalizations/run-1", chapterIdx))
                .willReturn("ABCDEF");

        adminService.uploadEvents(bookId, List.of(file));

        ArgumentCaptor<List<Event>> captor = ArgumentCaptor.forClass(List.class);
        verify(eventRepository, times(1)).saveAll(captor.capture());
        List<Event> savedEvents = captor.getValue();
        assertThat(savedEvents).hasSize(1);
        assertThat(savedEvents.get(0).getIdx()).isEqualTo(1);
        assertThat(savedEvents.get(0).getRawText()).isEqualTo("ABCDEF");
        verify(normalizedArtifactStorageService, times(1))
                .loadNormalizedChapterText("books/1/normalizations/run-1", chapterIdx);
    }

    @Test
    @DisplayName("uploadEvents rejects a chapter that already has events")
    void uploadEvents_throwsException_whenDataExists() throws IOException {
        Long bookId = 1L;
        int chapterIdx = 1;
        String fileName = String.format("chapter%d_events.json", chapterIdx);
        String jsonContent = """
                {
                  "chapterIndex": 1,
                  "items": []
                }
                """;
        MockMultipartFile file = new MockMultipartFile(
                "files",
                fileName,
                "application/json",
                jsonContent.getBytes(StandardCharsets.UTF_8)
        );

        Book book = Book.builder().id(bookId).normalizedArtifactPath("books/1/normalizations/run-1").build();
        Chapter chapter = Chapter.builder().id(10L).idx(chapterIdx).book(book).build();

        given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
        given(chapterRepository.findByBookIdAndIdx(bookId, chapterIdx)).willReturn(Optional.of(chapter));
        given(eventRepository.existsByChapter(chapter)).willReturn(true);

        GeneralException exception = assertThrows(GeneralException.class, () -> adminService.uploadEvents(bookId, List.of(file)));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.EVENT_DATA_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("uploadChapterSummaries saves summaries and updates chapter state")
    void uploadChapterSummaries_savesSummaries() throws IOException {
        Long bookId = 1L;
        int chapterIdx = 1;
        String fileName = String.format("chapter%d_perspective_summaries.json", chapterIdx);
        String jsonContent = """
                {
                  "chapterIndex": 1,
                  "language": "ko",
                  "items": [
                    {
                      "characterId": "10",
                      "characterName": "character-1",
                      "summary": "summary text"
                    }
                  ]
                }
                """;
        MockMultipartFile file = new MockMultipartFile(
                "files",
                fileName,
                "application/json",
                jsonContent.getBytes(StandardCharsets.UTF_8)
        );

        Book book = Book.builder().id(bookId).build();
        Chapter chapter = Chapter.builder().id(10L).idx(chapterIdx).book(book).build();
        Character character = Character.builder().id(1L).build();

        given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
        given(chapterRepository.findByBookIdAndIdx(bookId, chapterIdx)).willReturn(Optional.of(chapter));
        given(characterPovSummaryRepository.existsByChapter(chapter)).willReturn(false);
        given(characterRepository.findByBookAndCharacterId(book, 10L)).willReturn(Optional.of(character));
        given(chapterRepository.findByBookId(bookId)).willReturn(List.of(chapter));

        adminService.uploadChapterSummaries(bookId, List.of(file));

        verify(characterPovSummaryRepository, times(1)).saveAll(anyList());
        assertThat(chapter.isPovSummariesCached()).isTrue();
    }

    @Test
    @DisplayName("uploadChapterSummaries rejects a chapter that is already summarized")
    void uploadChapterSummaries_throwsException_whenSummaryExists() throws IOException {
        Long bookId = 1L;
        int chapterIdx = 1;
        String fileName = String.format("chapter%d_perspective_summaries.json", chapterIdx);
        String jsonContent = """
                {
                  "chapterIndex": 1,
                  "language": "ko",
                  "items": []
                }
                """;
        MockMultipartFile file = new MockMultipartFile(
                "files",
                fileName,
                "application/json",
                jsonContent.getBytes(StandardCharsets.UTF_8)
        );

        Book book = Book.builder().id(bookId).build();
        Chapter chapter = Chapter.builder().id(10L).idx(chapterIdx).book(book).build();

        given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
        given(chapterRepository.findByBookIdAndIdx(bookId, chapterIdx)).willReturn(Optional.of(chapter));
        given(characterPovSummaryRepository.existsByChapter(chapter)).willReturn(true);

        GeneralException exception = assertThrows(GeneralException.class, () -> adminService.uploadChapterSummaries(bookId, List.of(file)));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.CHAPTER_ALREADY_SUMMARIZED);
    }

    @Test
    @DisplayName("uploadRelationshipDeltas saves node weights and edges from delta payload")
    void uploadRelationshipDeltas_withCorrectJsonStructure() throws IOException {
        Long bookId = 1L;
        int chapterIdx = 1;
        int eventIdx = 1;
        String fileName = String.format("chapter%d_something_event_%d.json", chapterIdx, eventIdx);
        String jsonContent = """
                {
                  "contractVersion": "relationship-delta-v1",
                  "chapterIndex": 1,
                  "eventId": "ch1-e1",
                  "items": [
                    {
                      "fromCharacterId": "10",
                      "toCharacterId": "20",
                      "labels": ["friend"],
                      "positivity": 0.9,
                      "evidenceCount": 5,
                      "reason": "event reason"
                    }
                  ],
                  "nodeWeights": {
                    "10": {
                      "weight": 15.5,
                      "count": 3
                    }
                  }
                }
                """;
        MockMultipartFile file = new MockMultipartFile(
                "files",
                fileName,
                "application/json",
                jsonContent.getBytes(StandardCharsets.UTF_8)
        );

        Book book = Book.builder().id(bookId).build();
        Event event = Event.builder().id(100L).build();
        Character fromChar = Character.builder().id(10L).name("from").build();
        Character toChar = Character.builder().id(20L).name("to").build();

        given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
        given(eventRepository.findByBookIdAndChapterIdxAndEventIdx(bookId, chapterIdx, eventIdx)).willReturn(Optional.of(event));
        given(characterRepository.findByBookAndCharacterId(book, 10L)).willReturn(Optional.of(fromChar));
        given(characterRepository.findByBookAndCharacterId(book, 20L)).willReturn(Optional.of(toChar));

        adminService.uploadRelationshipDeltas(bookId, List.of(file));

        ArgumentCaptor<List<EventCharacterStat>> statCaptor = ArgumentCaptor.forClass(List.class);
        verify(statRepository, times(1)).saveAll(statCaptor.capture());
        List<EventCharacterStat> savedStats = statCaptor.getValue();
        assertThat(savedStats).hasSize(1);
        assertThat(savedStats.get(0).getCharacter().getId()).isEqualTo(10L);
        assertThat(savedStats.get(0).getNodeWeight()).isEqualTo(15.5);

        ArgumentCaptor<List<EventRelationshipEdge>> edgeCaptor = ArgumentCaptor.forClass(List.class);
        verify(eventRelationshipEdgeRepository, times(1)).saveAll(edgeCaptor.capture());
        List<EventRelationshipEdge> savedEdges = edgeCaptor.getValue();
        assertThat(savedEdges).hasSize(1);
        assertThat(savedEdges.get(0).getFromCharacter().getId()).isEqualTo(10L);
        assertThat(savedEdges.get(0).getToCharacter().getId()).isEqualTo(20L);
        assertThat(savedEdges.get(0).getSentimentScore()).isEqualTo(0.9f);
        assertThat(savedEdges.get(0).getInteractionCount()).isEqualTo(5);
        assertThat(savedEdges.get(0).getExplanation()).isEqualTo("event reason");
    }

    @Test
    @DisplayName("uploadRelationshipDeltas replaces existing event relationship data")
    void uploadRelationshipDeltas_replacesExistingEventData() throws IOException {
        Long bookId = 1L;
        int chapterIdx = 1;
        int eventIdx = 1;
        String fileName = String.format("chapter%d_something_event_%d.json", chapterIdx, eventIdx);
        String jsonContent = """
                {
                  "contractVersion": "relationship-delta-v1",
                  "chapterIndex": 1,
                  "eventId": "ch1-e1",
                  "items": [
                    {
                      "fromCharacterId": "10",
                      "toCharacterId": "20",
                      "labels": ["friend"],
                      "positivity": 0.9,
                      "evidenceCount": 1
                    }
                  ]
                }
                """;
        MockMultipartFile file = new MockMultipartFile(
                "files",
                fileName,
                "application/json",
                jsonContent.getBytes(StandardCharsets.UTF_8)
        );

        Book book = Book.builder().id(bookId).build();
        Event event = Event.builder().id(100L).build();
        Character fromChar = Character.builder().id(10L).name("from").build();
        Character toChar = Character.builder().id(20L).name("to").build();

        given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
        given(eventRepository.findByBookIdAndChapterIdxAndEventIdx(bookId, chapterIdx, eventIdx)).willReturn(Optional.of(event));
        given(characterRepository.findByBookAndCharacterId(book, 10L)).willReturn(Optional.of(fromChar));
        given(characterRepository.findByBookAndCharacterId(book, 20L)).willReturn(Optional.of(toChar));
        given(eventRelationshipEdgeRepository.existsByEvent(event)).willReturn(true);

        adminService.uploadRelationshipDeltas(bookId, List.of(file));

        verify(eventRelationshipEdgeRepository, times(1)).deleteByEvent(event);
        verify(eventRelationshipEdgeRepository, times(1)).saveAll(anyList());
    }

    @Test
    @DisplayName("deleteRelationships deletes edge data for an event")
    void deleteRelationships_callsDeleteByEvent() {
        Long bookId = 1L;
        int chapterIdx = 1;
        int eventIdx = 1;
        Chapter chapter = Chapter.builder().id(10L).build();
        Event event = Event.builder().id(100L).build();

        given(chapterRepository.findByBookIdAndIdx(bookId, chapterIdx)).willReturn(Optional.of(chapter));
        given(eventRepository.findByChapterAndIdx(chapter, eventIdx)).willReturn(Optional.of(event));
        given(eventRelationshipEdgeRepository.existsByEvent(event)).willReturn(true);
        given(statRepository.existsByEvent(event)).willReturn(false);

        adminService.deleteRelationships(bookId, chapterIdx, eventIdx);

        verify(eventRelationshipEdgeRepository, times(1)).deleteByEvent(event);
    }

    @Test
    @DisplayName("deleteCharacters deletes by book when character data exists")
    void deleteCharacters_callsDeleteByBook() {
        Long bookId = 1L;
        Book book = Book.builder().id(bookId).build();

        given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
        given(characterRepository.existsByBook(book)).willReturn(true);

        adminService.deleteCharacters(bookId);

        verify(characterRepository, times(1)).deleteByBook(book);
    }

    @Test
    @DisplayName("deleteCharacters rejects when no character data exists")
    void deleteCharacters_throwsException_whenNoData() {
        Long bookId = 1L;
        Book book = Book.builder().id(bookId).build();

        given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
        given(characterRepository.existsByBook(book)).willReturn(false);

        GeneralException exception = assertThrows(GeneralException.class, () -> adminService.deleteCharacters(bookId));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.NO_CHARACTERS_TO_DELETE);
    }

    @Test
    @DisplayName("deleteEvents deletes by chapter when event data exists")
    void deleteEvents_callsDeleteByChapter() {
        Long bookId = 1L;
        int chapterIdx = 1;
        Chapter chapter = Chapter.builder().id(10L).build();

        given(chapterRepository.findByBookIdAndIdx(bookId, chapterIdx)).willReturn(Optional.of(chapter));
        given(eventRepository.existsByChapter(chapter)).willReturn(true);

        adminService.deleteEvents(bookId, chapterIdx);

        verify(eventRepository, times(1)).deleteByChapter(chapter);
    }

    @Test
    @DisplayName("deleteEvents rejects when a chapter has no events")
    void deleteEvents_throwsException_whenNoData() {
        Long bookId = 1L;
        int chapterIdx = 1;
        Chapter chapter = Chapter.builder().id(10L).build();

        given(chapterRepository.findByBookIdAndIdx(bookId, chapterIdx)).willReturn(Optional.of(chapter));
        given(eventRepository.existsByChapter(chapter)).willReturn(false);

        GeneralException exception = assertThrows(GeneralException.class, () -> adminService.deleteEvents(bookId, chapterIdx));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.NO_EVENTS_TO_DELETE);
    }

    @Test
    @DisplayName("deleteChapterSummary deletes summaries and resets chapter summary state")
    void deleteChapterSummary_deletesAndUpdatesStatus() {
        Long bookId = 1L;
        int chapterIdx = 1;
        Chapter chapter = Chapter.builder().id(10L).build();
        chapter.markAsSummarized();

        given(chapterRepository.findByBookIdAndIdx(bookId, chapterIdx)).willReturn(Optional.of(chapter));
        given(characterPovSummaryRepository.existsByChapter(chapter)).willReturn(true);

        adminService.deleteChapterSummary(bookId, chapterIdx);

        verify(characterPovSummaryRepository, times(1)).deleteByChapter(chapter);
        assertThat(chapter.isPovSummariesCached()).isFalse();
    }
}
