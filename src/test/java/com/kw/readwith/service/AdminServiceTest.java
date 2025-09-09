package com.kw.readwith.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.Chapter;
import com.kw.readwith.domain.Character;
import com.kw.readwith.domain.Event;
import com.kw.readwith.domain.mapping.EventCharacterStat;
import com.kw.readwith.domain.mapping.EventRelationshipEdge;
import com.kw.readwith.dto.admin.UnsummarizedItemDTO;
import com.kw.readwith.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
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

    @Spy
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("요약 안된 챕터 조회 시, DTO 리스트를 정확히 반환한다.")
    void getUnsummarizedChapters() {
        // given
        Book mockBook = Book.builder().id(1L).title("테스트 책").build();
        Chapter mockChapter = Chapter.builder().id(10L).title("테스트 챕터").book(mockBook).build();
        List<Chapter> mockChapters = List.of(mockChapter);

        given(chapterRepository.findUnsummarizedChapters()).willReturn(mockChapters);

        // when
        List<UnsummarizedItemDTO> result = adminService.getUnsummarizedChapters();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(10L);
        assertThat(result.get(0).getName()).isEqualTo("테스트 챕터"); // 실제 필드명 'name' 사용
        assertThat(result.get(0).getBookTitle()).isEqualTo("테스트 책"); // 실제 필드명 'bookTitle' 사용
        verify(chapterRepository, times(1)).findUnsummarizedChapters();
    }
    
    @Test
    @DisplayName("인물 정보 업로드 시, 새로운 인물만 정확히 저장된다.")
    void uploadCharacters_savesNewCharacters() throws IOException {
        // given
        Long bookId = 1L;
        String jsonContent = "{\"characters\": [{\"id\": 1, \"common_name\": \"새로운 인물\", \"names\": [\"별명\"], \"main_character\": true, \"description\": \"설명\", \"portrait_prompt\": \"프롬프트\"}]}";
        MockMultipartFile file = new MockMultipartFile("file", "characters.json", "application/json", jsonContent.getBytes());

        Book mockBook = Book.builder().id(bookId).build();
        given(bookRepository.findById(bookId)).willReturn(Optional.of(mockBook));
        // '새로운 인물'은 DB에 없다고 가정
        given(characterRepository.findByBookAndName(mockBook, "새로운 인물")).willReturn(Optional.empty());

        // when
        adminService.uploadCharacters(bookId, file);

        // then
        ArgumentCaptor<List<Character>> captor = ArgumentCaptor.forClass(List.class);
        verify(characterRepository, times(1)).saveAll(captor.capture());
        List<Character> savedCharacters = captor.getValue();
        assertThat(savedCharacters).hasSize(1);
        assertThat(savedCharacters.get(0).getName()).isEqualTo("새로운 인물");
    }

    @Test
    @DisplayName("이벤트 정보 업로드 시, 데이터를 정확히 저장한다.")
    void uploadEvents_savesEvents() throws IOException {
        // given
        Long bookId = 1L;
        int chapterIdx = 1;
        String fileName = String.format("chapter%d_events.json", chapterIdx);
        String jsonContent = "[{\"event_id\": 1, \"start\": 0, \"end\": 100, \"text\": \"이벤트 내용\"}]";
        MockMultipartFile file = new MockMultipartFile("files", fileName, "application/json", jsonContent.getBytes());

        Book mockBook = Book.builder().id(bookId).build();
        Chapter mockChapter = Chapter.builder().id(10L).book(mockBook).build();

        given(bookRepository.findById(bookId)).willReturn(Optional.of(mockBook));
        given(chapterRepository.findByBookIdAndIdx(bookId, chapterIdx)).willReturn(Optional.of(mockChapter));
        given(eventRepository.existsByChapter(mockChapter)).willReturn(false);

        // when
        adminService.uploadEvents(bookId, List.of(file));

        // then
        ArgumentCaptor<List<Event>> captor = ArgumentCaptor.forClass(List.class);
        verify(eventRepository, times(1)).saveAll(captor.capture());
        List<Event> savedEvents = captor.getValue();
        assertThat(savedEvents).hasSize(1);
        assertThat(savedEvents.get(0).getIdx()).isEqualTo(1);
        assertThat(savedEvents.get(0).getRawText()).isEqualTo("이벤트 내용");
    }

    @Test
    @DisplayName("이벤트 정보 업로드 시, 데이터가 이미 존재하면 예외를 발생시킨다.")
    void uploadEvents_throwsException_whenDataExists() throws IOException {
        // given
        Long bookId = 1L;
        int chapterIdx = 1;
        String fileName = String.format("chapter%d_events.json", chapterIdx);
        MockMultipartFile file = new MockMultipartFile("files", fileName, "application/json", "[]".getBytes());

        Book mockBook = Book.builder().id(bookId).build();
        Chapter mockChapter = Chapter.builder().id(10L).book(mockBook).build();

        given(bookRepository.findById(bookId)).willReturn(Optional.of(mockBook));
        given(chapterRepository.findByBookIdAndIdx(bookId, chapterIdx)).willReturn(Optional.of(mockChapter));
        // 데이터가 이미 존재한다고 가정
        given(eventRepository.existsByChapter(mockChapter)).willReturn(true);

        // when & then
        GeneralException exception = assertThrows(GeneralException.class, () -> adminService.uploadEvents(bookId, List.of(file)));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.EVENT_DATA_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("챕터 요약본 업로드 시, 데이터를 정확히 저장한다.")
    void uploadChapterSummaries_savesSummaries() throws IOException {
        // given
        Long bookId = 1L;
        int chapterIdx = 1;
        String fileName = String.format("chapter%d_perspective_summaries.json", chapterIdx);
        String jsonContent = "{\"10\": {\"character_name\": \"인물1\", \"summary\": \"요약 내용\"}}";
        MockMultipartFile file = new MockMultipartFile("files", fileName, "application/json", jsonContent.getBytes());

        Book mockBook = Book.builder().id(bookId).build();
        Chapter mockChapter = Chapter.builder().id(10L).book(mockBook).build();
        Character mockCharacter = Character.builder().id(1L).build();

        given(bookRepository.findById(bookId)).willReturn(Optional.of(mockBook));
        given(chapterRepository.findByBookIdAndIdx(bookId, chapterIdx)).willReturn(Optional.of(mockChapter));
        given(characterPovSummaryRepository.existsByChapter(mockChapter)).willReturn(false);
        given(characterRepository.findByBookAndCharacterId(mockBook, 10L)).willReturn(Optional.of(mockCharacter));

        // when
        adminService.uploadChapterSummaries(bookId, List.of(file));

        // then
        verify(characterPovSummaryRepository, times(1)).saveAll(anyList());
        assertThat(mockChapter.isPovSummariesCached()).isTrue();
    }

    @Test
    @DisplayName("챕터 요약본 업로드 시, 요약본이 이미 존재하면 예외를 발생시킨다.")
    void uploadChapterSummaries_throwsException_whenSummaryExists() throws IOException {
        // given
        Long bookId = 1L;
        int chapterIdx = 1;
        String fileName = String.format("chapter%d_perspective_summaries.json", chapterIdx);
        MockMultipartFile file = new MockMultipartFile("files", fileName, "application/json", "{}".getBytes());

        Book mockBook = Book.builder().id(bookId).build();
        Chapter mockChapter = Chapter.builder().id(10L).book(mockBook).build();

        given(bookRepository.findById(bookId)).willReturn(Optional.of(mockBook));
        given(chapterRepository.findByBookIdAndIdx(bookId, chapterIdx)).willReturn(Optional.of(mockChapter));
        // 요약본이 이미 존재한다고 가정
        given(characterPovSummaryRepository.existsByChapter(mockChapter)).willReturn(true);

        // when & then
        GeneralException exception = assertThrows(GeneralException.class, () -> adminService.uploadChapterSummaries(bookId, List.of(file)));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.CHAPTER_ALREADY_SUMMARIZED);
    }

    @Test
    @DisplayName("관계 정보 파일 업로드 시, DTO 구조에 맞는 JSON을 파싱하여 관계 데이터를 정확히 저장한다.")
    void uploadRelationships_withCorrectJsonStructure() throws IOException {
        // given
        Long bookId = 1L;
        int chapterIdx = 1;
        int eventIdx = 1;
        String fileName = String.format("chapter%d_something_event_%d.json", chapterIdx, eventIdx);

        // 실제 DTO 구조와 완벽하게 일치하는 JSON 문자열 생성
        String jsonContent = "{\n" +
                "  \"relations\": [\n" +
                "    {\n" +
                "      \"id1\": 10,\n" +
                "      \"id2\": 20,\n" +
                "      \"relation\": [\"friend\"],\n" +
                "      \"positivity\": 0.9,\n" +
                "      \"weight\": 0.8,\n" +
                "      \"count\": 5\n" +
                "    }\n" +
                "  ],\n" +
                "  \"node_weights_accum\": {\n" +
                "    \"10\": {\n" +
                "      \"weight\": 15.5,\n" +
                "      \"count\": 3\n" +
                "    }\n" +
                "  },\n" +
                "  \"log\": {}\n" +
                "}";

        MockMultipartFile mockFile = new MockMultipartFile("files", fileName, "application/json", jsonContent.getBytes(StandardCharsets.UTF_8));

        Book mockBook = Book.builder().id(bookId).build();
        Event mockEvent = Event.builder().id(100L).build();
        Character fromChar = Character.builder().id(10L).name("캐릭터1").build();
        Character toChar = Character.builder().id(20L).name("캐릭터2").build();

        // Mock Repository 설정
        given(bookRepository.findById(bookId)).willReturn(Optional.of(mockBook));
        given(eventRepository.findByBookIdAndChapterIdxAndEventIdx(bookId, chapterIdx, eventIdx)).willReturn(Optional.of(mockEvent));
        given(characterRepository.findByBookAndCharacterId(mockBook, 10L)).willReturn(Optional.of(fromChar));
        given(characterRepository.findByBookAndCharacterId(mockBook, 20L)).willReturn(Optional.of(toChar));
        given(statRepository.findByEventAndCharacter(any(), any())).willReturn(Optional.empty());
        given(eventRelationshipEdgeRepository.existsByEventAndFromCharacterAndToCharacter(any(), any(), any())).willReturn(false);

        // when
        adminService.uploadRelationships(bookId, List.of(mockFile));

        // then
        // 1. 가중치(Stat) 저장 검증
        ArgumentCaptor<List<EventCharacterStat>> statCaptor = ArgumentCaptor.forClass(List.class);
        verify(statRepository, times(1)).saveAll(statCaptor.capture());
        List<EventCharacterStat> savedStats = statCaptor.getValue();
        assertThat(savedStats).hasSize(1);
        assertThat(savedStats.get(0).getCharacter().getId()).isEqualTo(10L);
        assertThat(savedStats.get(0).getNodeWeight()).isEqualTo(15.5);

        // 2. 관계(Edge) 저장 검증
        ArgumentCaptor<List<EventRelationshipEdge>> edgeCaptor = ArgumentCaptor.forClass(List.class);
        verify(eventRelationshipEdgeRepository, times(1)).saveAll(edgeCaptor.capture());
        List<EventRelationshipEdge> savedEdges = edgeCaptor.getValue();
        assertThat(savedEdges).hasSize(1);
        assertThat(savedEdges.get(0).getFromCharacter().getId()).isEqualTo(10L);
        assertThat(savedEdges.get(0).getToCharacter().getId()).isEqualTo(20L);
        assertThat(savedEdges.get(0).getSentimentScore()).isEqualTo(0.9f);
        assertThat(savedEdges.get(0).getInteractionCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("관계 정보 업로드 시, 데이터가 이미 존재하면 예외를 발생시킨다.")
    void uploadRelationships_throwsException_whenDataExists() throws IOException {
        // given
        Long bookId = 1L;
        int chapterIdx = 1;
        int eventIdx = 1;
        String fileName = String.format("chapter%d_something_event_%d.json", chapterIdx, eventIdx);
        // 테스트에 필요한 최소한의 JSON 구조
        String jsonContent = "{\"relations\": [{\"id1\": 10, \"id2\": 20, \"relation\": [\"friend\"], \"positivity\": 0.9, \"count\": 1}]}";
        MockMultipartFile mockFile = new MockMultipartFile("files", fileName, "application/json", jsonContent.getBytes(StandardCharsets.UTF_8));

        Book mockBook = Book.builder().id(bookId).build();
        Event mockEvent = Event.builder().id(100L).build();
        Character fromChar = Character.builder().id(10L).name("캐릭터1").build();
        Character toChar = Character.builder().id(20L).name("캐릭터2").build();

        given(bookRepository.findById(bookId)).willReturn(Optional.of(mockBook));
        given(eventRepository.findByBookIdAndChapterIdxAndEventIdx(bookId, chapterIdx, eventIdx)).willReturn(Optional.of(mockEvent));
        given(characterRepository.findByBookAndCharacterId(mockBook, 10L)).willReturn(Optional.of(fromChar));
        given(characterRepository.findByBookAndCharacterId(mockBook, 20L)).willReturn(Optional.of(toChar));

        // 관계가 이미 존재한다고 가정
        given(eventRelationshipEdgeRepository.existsByEventAndFromCharacterAndToCharacter(mockEvent, fromChar, toChar)).willReturn(true);

        // when & then
        GeneralException exception = assertThrows(GeneralException.class, () -> {
            adminService.uploadRelationships(bookId, List.of(mockFile));
        });

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.RELATIONSHIP_DATA_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("관계 정보 삭제 시, 올바른 Event로 Repository의 deleteByEvent를 호출한다.")
    void deleteRelationships_callsDeleteByEvent() {
        // given
        Long bookId = 1L;
        int chapterIdx = 1;
        int eventIdx = 1;
        Chapter mockChapter = Chapter.builder().id(10L).build();
        Event mockEvent = Event.builder().id(100L).build();

        given(chapterRepository.findByBookIdAndIdx(bookId, chapterIdx)).willReturn(Optional.of(mockChapter));
        given(eventRepository.findByChapterAndIdx(mockChapter, eventIdx)).willReturn(Optional.of(mockEvent));
        // 삭제할 데이터가 존재한다고 가정
        given(eventRelationshipEdgeRepository.existsByEvent(mockEvent)).willReturn(true);

        // when
        adminService.deleteRelationships(bookId, chapterIdx, eventIdx);

        // then
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventRelationshipEdgeRepository, times(1)).deleteByEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getId()).isEqualTo(mockEvent.getId());
    }

    @Test
    @DisplayName("인물 정보 삭제 시, 올바른 Book으로 Repository의 deleteByBook을 호출한다.")
    void deleteCharacters_callsDeleteByBook() {
        // given
        Long bookId = 1L;
        Book mockBook = Book.builder().id(bookId).build();

        given(bookRepository.findById(bookId)).willReturn(Optional.of(mockBook));
        // 삭제할 데이터가 존재한다고 가정
        given(characterRepository.existsByBook(mockBook)).willReturn(true);

        // when
        adminService.deleteCharacters(bookId);

        // then
        verify(characterRepository, times(1)).deleteByBook(mockBook);
    }

    @Test
    @DisplayName("인물 정보 삭제 시, 삭제할 데이터가 없으면 예외를 발생시킨다.")
    void deleteCharacters_throwsException_whenNoData() {
        // given
        Long bookId = 1L;
        Book mockBook = Book.builder().id(bookId).build();

        given(bookRepository.findById(bookId)).willReturn(Optional.of(mockBook));
        // 삭제할 데이터가 없다고 가정
        given(characterRepository.existsByBook(mockBook)).willReturn(false);

        // when & then
        GeneralException exception = assertThrows(GeneralException.class, () -> adminService.deleteCharacters(bookId));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.NO_CHARACTERS_TO_DELETE);
    }

    @Test
    @DisplayName("이벤트 정보 삭제 시, 올바른 Chapter로 Repository의 deleteByChapter를 호출한다.")
    void deleteEvents_callsDeleteByChapter() {
        // given
        Long bookId = 1L;
        int chapterIdx = 1;
        Chapter mockChapter = Chapter.builder().id(10L).build();

        given(chapterRepository.findByBookIdAndIdx(bookId, chapterIdx)).willReturn(Optional.of(mockChapter));
        given(eventRepository.existsByChapter(mockChapter)).willReturn(true);

        // when
        adminService.deleteEvents(bookId, chapterIdx);

        // then
        verify(eventRepository, times(1)).deleteByChapter(mockChapter);
    }

    @Test
    @DisplayName("이벤트 정보 삭제 시, 삭제할 데이터가 없으면 예외를 발생시킨다.")
    void deleteEvents_throwsException_whenNoData() {
        // given
        Long bookId = 1L;
        int chapterIdx = 1;
        Chapter mockChapter = Chapter.builder().id(10L).build();

        given(chapterRepository.findByBookIdAndIdx(bookId, chapterIdx)).willReturn(Optional.of(mockChapter));
        given(eventRepository.existsByChapter(mockChapter)).willReturn(false);

        // when & then
        GeneralException exception = assertThrows(GeneralException.class, () -> adminService.deleteEvents(bookId, chapterIdx));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.NO_EVENTS_TO_DELETE);
    }

    @Test
    @DisplayName("챕터 요약본 삭제 시, Repository의 deleteByChapter를 호출하고 챕터 상태를 변경한다.")
    void deleteChapterSummary_deletesAndUpdatesStatus() {
        // given
        Long bookId = 1L;
        int chapterIdx = 1;
        Chapter mockChapter = Chapter.builder().id(10L).build();
        mockChapter.markAsSummarized(); // 초기 상태: 요약 완료

        given(chapterRepository.findByBookIdAndIdx(bookId, chapterIdx)).willReturn(Optional.of(mockChapter));
        given(characterPovSummaryRepository.existsByChapter(mockChapter)).willReturn(true);

        // when
        adminService.deleteChapterSummary(bookId, chapterIdx);

        // then
        verify(characterPovSummaryRepository, times(1)).deleteByChapter(mockChapter);
        assertThat(mockChapter.isPovSummariesCached()).isFalse(); // 상태가 '미완료'로 변경되었는지 확인
    }
}
