package com.kw.readwith.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.Chapter;
import com.kw.readwith.domain.Character;
import com.kw.readwith.domain.Event;
import com.kw.readwith.domain.mapping.CharacterPovSummary;
import com.kw.readwith.domain.mapping.EventCharacterStat;
import com.kw.readwith.domain.mapping.EventRelationshipEdge;
import com.kw.readwith.dto.admin.*;
import com.kw.readwith.dto.book.BookSummaryDTO;
import com.kw.readwith.repository.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // 기본적으로 읽기 전용 트랜잭션으로 설정
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    // 모든 관리자 기능에 필요한 Repository 및 ObjectMapper 의존성 주입
    private final BookRepository bookRepository;
    private final CharacterRepository characterRepository;
    private final ChapterRepository chapterRepository;
    private final EventRepository eventRepository;
    private final EventRelationshipEdgeRepository eventRelationshipEdgeRepository;
    private final CharacterPovSummaryRepository characterPovSummaryRepository;
    private final EventCharacterStatRepository statRepository; // 의존성 추가
    private final ObjectMapper objectMapper;

    // 파일명에서 챕터와 이벤트 인덱스를 추출하기 위한 정규표현식 패턴 (새로 추가)
    private static final Pattern RELATIONSHIP_FILE_PATTERN = Pattern.compile("chapter(\\d+)_.*?_event_(\\d+)\\.json");

    /*
     * =====================================================================================
     * 1. 데이터 업로드
     * =====================================================================================
     */

    /**
     * 등장인물 정보가 담긴 JSON 파일을 업로드합니다.
     */
    @Transactional
    public void uploadCharacters(Long bookId, MultipartFile file) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));

        try {
            CharacterDTO.CharacterListDTO characterListDTO = objectMapper.readValue(file.getInputStream(), CharacterDTO.CharacterListDTO.class);

            List<Character> newCharacters = new ArrayList<>();
            for (CharacterDTO dto : characterListDTO.getCharacters()) {
                // 중복 저장을 방지하기 위해, 해당 이름의 캐릭터가 없는 경우에만 추가
                Optional<Character> existingCharacter = characterRepository.findByBookAndName(book, dto.getCommon_name());

                if (existingCharacter.isEmpty()) {
                    Character character = Character.builder()
                            .book(book)
                            .characterId(dto.getId().longValue())
                            .name(dto.getCommon_name())
                            .names(String.join(",", dto.getNames()))
                            .isMainCharacter(dto.isMain_character())
                            .personalityText(dto.getDescription())
                            .profileText(dto.getPortrait_prompt())
                            .build();
                    newCharacters.add(character);
                }
            }

            if (!newCharacters.isEmpty()) {
                characterRepository.saveAll(newCharacters);
            }

        } catch (IOException e) {
            throw new GeneralException(ErrorStatus.JSON_PARSING_ERROR);
        }
    }

    /**
     * 여러 챕터의 이벤트 JSON 파일들을 한번에 업로드합니다.
     * 파일 이름(chapter<번호>_events.json)에서 챕터 번호를 자동으로 인식합니다.
     */
    @Transactional
    public void uploadEvents(Long bookId, List<MultipartFile> eventFiles) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));

        List<Event> allNewEvents = new ArrayList<>();
        // 파일명에서 챕터 번호를 추출하기 위한 정규표현식
        Pattern pattern = Pattern.compile("chapter(\\d+)_events\\.json");

        for (MultipartFile eventFile : eventFiles) {
            String filename = eventFile.getOriginalFilename();
            Matcher matcher = pattern.matcher(filename);

            if (!matcher.matches()) {
                throw new GeneralException(ErrorStatus.INVALID_FILE_NAME_FORMAT, "파일명: " + filename);
            }

            Integer chapterIdx = Integer.parseInt(matcher.group(1));
            Chapter chapter = chapterRepository.findByBookIdAndIdx(bookId, chapterIdx)
                    .orElseThrow(() -> new GeneralException(ErrorStatus.CHAPTER_NOT_FOUND, "챕터 인덱스 " + chapterIdx + "를 찾을 수 없습니다."));

            // 이미 데이터가 있는 챕터는 업로드 불가
            if (eventRepository.existsByChapter(chapter)) {
                throw new GeneralException(ErrorStatus.EVENT_DATA_ALREADY_EXISTS, "챕터 " + chapterIdx + "의 이벤트는 이미 존재합니다.");
            }

            try {
                List<EventDTO> eventDTOs = objectMapper.readValue(eventFile.getInputStream(), new TypeReference<List<EventDTO>>() {});
                List<Event> newEventsFromFile = eventDTOs.stream()
                        .map(dto -> Event.builder()
                                .book(book)
                                .chapter(chapter)
                                .idx(dto.getEventId())
                                .startPos(dto.getStart())
                                .endPos(dto.getEnd())
                                .rawText(dto.getText())
                                .build())
                        .collect(Collectors.toList());
                allNewEvents.addAll(newEventsFromFile);
            } catch (IOException e) {
                throw new GeneralException(ErrorStatus.JSON_PARSING_ERROR);
            }
        }

        // 모든 파일 유효성 검사가 끝난 후, DB에 한번에 저장
        if (!allNewEvents.isEmpty()) {
            eventRepository.saveAll(allNewEvents);
        }
    }

    /**
     * 여러 챕터의 POV 요약 JSON 파일들을 한번에 업로드합니다.
     */
    @Transactional
    public void uploadChapterSummaries(Long bookId, List<MultipartFile> summaryFiles) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));

        List<CharacterPovSummary> allNewSummaries = new ArrayList<>();
        Pattern pattern = Pattern.compile("chapter(\\d+)_perspective_summaries\\.json");

        for (MultipartFile summaryFile : summaryFiles) {
            String filename = summaryFile.getOriginalFilename();
            Matcher matcher = pattern.matcher(filename);

            if (!matcher.matches()) {
                throw new GeneralException(ErrorStatus.INVALID_FILE_NAME_FORMAT, "파일명: " + filename);
            }

            Integer chapterIdx = Integer.parseInt(matcher.group(1));
            Chapter chapter = chapterRepository.findByBookIdAndIdx(bookId, chapterIdx)
                    .orElseThrow(() -> new GeneralException(ErrorStatus.CHAPTER_NOT_FOUND, "챕터 인덱스 " + chapterIdx + "를 찾을 수 없습니다."));

            if (characterPovSummaryRepository.existsByChapter(chapter)) {
                throw new GeneralException(ErrorStatus.CHAPTER_ALREADY_SUMMARIZED, "챕터 " + chapterIdx + "의 요약본은 이미 존재합니다.");
            }

            Map<String, PovSummaryData> summaries;
            try {
                summaries = objectMapper.readValue(summaryFile.getInputStream(), new TypeReference<>() {});
            } catch (IOException e) {
                throw new GeneralException(ErrorStatus.JSON_PARSING_ERROR);
            }

            List<CharacterPovSummary> newSummariesFromFile = summaries.entrySet().stream().map(entry -> {
                Long characterId = Long.parseLong(entry.getKey());
                PovSummaryData summaryData = entry.getValue();
                Character character = characterRepository.findByBookAndCharacterId(chapter.getBook(), characterId)
                        .orElseThrow(() -> new GeneralException(ErrorStatus.CHARACTER_NOT_FOUND, "Character not found with jsonId: " + characterId));

                return CharacterPovSummary.builder()
                        .book(chapter.getBook())
                        .chapter(chapter)
                        .character(character)
                        .summaryText(summaryData.getSummary())
                        .build();
            }).collect(Collectors.toList());

            allNewSummaries.addAll(newSummariesFromFile);
            // 해당 챕터의 요약이 완료되었음을 표시
            chapter.markAsSummarized();
        }

        if (!allNewSummaries.isEmpty()) {
            characterPovSummaryRepository.saveAll(allNewSummaries);
        }

        // 모든 챕터의 요약이 완료되었는지 확인 후, 책의 전체 요약 상태 업데이트
        checkAndUpdateBookSummaryStatus(book);
    }

    /**
     * 여러 관계 JSON 파일을 업로드하고 DB에 저장
     * 파일 이름 규칙: chapter<번호>_..._event_<번호>.json
     *
     * @param bookId 관계 정보를 추가할 책의 ID
     * @param files  업로드할 관계 정보 JSON 파일 목록
     */
    @Transactional
    public void uploadRelationships(Long bookId, List<MultipartFile> files) {
        // 입력된 bookId로 Book 엔터티를 조회
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));

        // 업로드된 각 파일에 대해 반복 처리를 시작
        for (MultipartFile file : files) {
            String filename = file.getOriginalFilename();
            // 파일명 규칙을 기반으로 챕터와 이벤트 인덱스 추출
            Matcher matcher = RELATIONSHIP_FILE_PATTERN.matcher(filename);

            if (!matcher.find()) {
                throw new GeneralException(ErrorStatus.INVALID_FILE_NAME_FORMAT, "파일명 형식이 올바르지 않습니다: " + filename);
            }

            // 정규표현식 그룹에서 챕터와 이벤트 인덱스 추출
            int chapterIdx = Integer.parseInt(matcher.group(1));
            int eventIdx = Integer.parseInt(matcher.group(2));

            // 파일명에서 얻은 정보로 Event 엔터티를 DB에서 조회
            Event event = eventRepository.findByBookIdAndChapterIdxAndEventIdx(bookId, chapterIdx, eventIdx)
                    .orElseThrow(() -> new GeneralException(ErrorStatus.EVENT_NOT_FOUND,
                            String.format("이벤트를 찾을 수 없습니다 (책 ID: %d, 챕터: %d, 이벤트: %d)", bookId, chapterIdx, eventIdx)));

            try {
                // JSON 파일을 DTO 객체로 변환
                RelationshipUploadDTO dto = objectMapper.readValue(file.getInputStream(), RelationshipUploadDTO.class);

                // DTO의 각 부분을 처리하는 헬퍼 메서드 호출
                processNodeWeights(event, book, dto.getNodeWeightsAccum());
                processRelations(event, book, dto.getRelations());

            } catch (IOException e) {
                throw new GeneralException(ErrorStatus.JSON_PARSING_ERROR, "관계 정보 JSON 파일 파싱에 실패했습니다: " + filename);
            }
        }
    }

    /**
     * JSON의 'node_weights_accum' 부분을 처리하는 헬퍼 메서드
     * 각 캐릭터의 가중치(weight)를 저장하기 전, 중복 데이터가 있는지 확인
     *
     * @param event          현재 처리 중인 이벤트 엔터티
     * @param book           현재 처리 중인 책 엔터티
     * @param nodeWeightsMap JSON에서 파싱된 캐릭터 ID와 가중치 정보가 담긴 맵
     */
    private void processNodeWeights(Event event, Book book, Map<String, NodeWeightDTO> nodeWeightsMap) {
        if (nodeWeightsMap == null) return;

        List<EventCharacterStat> newStats = new ArrayList<>();
        for (Map.Entry<String, NodeWeightDTO> entry : nodeWeightsMap.entrySet()) {
            Long characterBookId = Long.parseLong(entry.getKey());
            NodeWeightDTO weightDTO = entry.getValue();

            Character character = characterRepository.findByBookAndCharacterId(book, characterBookId)
                    .orElseThrow(() -> new GeneralException(ErrorStatus.CHARACTER_NOT_FOUND, "책에 정의된 ID가 " + characterBookId + "인 캐릭터를 찾을 수 없습니다."));

            // 기존 데이터가 있는지 확인
            if (statRepository.findByEventAndCharacter(event, character).isPresent()) {
                throw new GeneralException(ErrorStatus.NODE_WEIGHT_ALREADY_EXISTS,
                        String.format("해당 이벤트에 대한 '%s' 캐릭터의 가중치 데이터가 이미 존재합니다.", character.getName()));
            }

            EventCharacterStat stat = EventCharacterStat.builder()
                    .event(event)
                    .character(character)
                    .nodeWeight(weightDTO.getWeight())
                    .build();
            
            // EventCharacterStat에는 count 필드가 없으므로 로그만 출력
            log.info("Character {} weight: {}, count: {} (count not stored in EventCharacterStat)", 
                    character.getName(), weightDTO.getWeight(), weightDTO.getCount());
            newStats.add(stat);
        }

        // 모든 검사가 끝난 후, 한번에 저장
        statRepository.saveAll(newStats);
    }

    /**
     * JSON의 'relations' 부분을 처리하는 헬퍼 메서드
     * 인물 관계를 저장하기 전, 중복 데이터가 있는지 확인
     *
     * @param event        현재 처리 중인 이벤트 엔터티
     * @param book         현재 처리 중인 책 엔터티
     * @param relationDTOs JSON에서 파싱된 관계 정보 DTO 목록
     */
    private void processRelations(Event event, Book book, List<RelationshipDTO> relationDTOs) {
        if (relationDTOs == null || relationDTOs.isEmpty()) {
            return;
        }

        List<EventRelationshipEdge> newEdges = new ArrayList<>();
        for (RelationshipDTO dto : relationDTOs) {
            Character fromChar = characterRepository.findByBookAndCharacterId(book, dto.getId1())
                    .orElseThrow(() -> new GeneralException(ErrorStatus.CHARACTER_NOT_FOUND, "id1에 해당하는 캐릭터(ID: " + dto.getId1() + ")를 찾을 수 없습니다."));
            Character toChar = characterRepository.findByBookAndCharacterId(book, dto.getId2())
                    .orElseThrow(() -> new GeneralException(ErrorStatus.CHARACTER_NOT_FOUND, "id2에 해당하는 캐릭터(ID: " + dto.getId2() + ")를 찾을 수 없습니다."));

            // 관계가 (A->B) 또는 (B->A) 방향으로 이미 존재하는지 확인
            if (eventRelationshipEdgeRepository.existsByEventAndFromCharacterAndToCharacter(event, fromChar, toChar) ||
                    eventRelationshipEdgeRepository.existsByEventAndFromCharacterAndToCharacter(event, toChar, fromChar)) {
                throw new GeneralException(ErrorStatus.RELATIONSHIP_DATA_ALREADY_EXISTS,
                        String.format("해당 이벤트에 대한 '%s'와(과) '%s'의 관계 데이터가 이미 존재합니다.", fromChar.getName(), toChar.getName()));
            }

            try {
                EventRelationshipEdge edge = EventRelationshipEdge.builder()
                        .event(event)
                        .fromCharacter(fromChar)
                        .toCharacter(toChar)
                        .sentimentScore(dto.getPositivity().floatValue())
                        .interactionCount(dto.getCount())
                        .relationTags(objectMapper.writeValueAsString(dto.getRelation()))
                        .build();
                newEdges.add(edge);
            } catch (JsonProcessingException e) {
                throw new GeneralException(ErrorStatus.JSON_PARSING_ERROR, String.format("캐릭터 %d와(과) %d의 관계 태그 처리 중 오류가 발생했습니다.", dto.getId1(), dto.getId2()));
            }
        }

        // 모든 검사가 끝난 후, 한번에 저장
        if (!newEdges.isEmpty()) {
            eventRelationshipEdgeRepository.saveAll(newEdges);
        }
    }

    /*
     * =====================================================================================
     * 2. 데이터 조회
     * =====================================================================================
     */

    /**
     * 전체 요약이 완료되지 않은 책 목록을 조회합니다.
     */
    public List<BookSummaryDTO> getUnsummarizedBooks() {
        List<Book> books = bookRepository.findBySummaryIsFalse();
        return books.stream()
                .map(book -> BookSummaryDTO.builder()
                        .id(book.getId())
                        .title(book.getTitle())
                        .author(book.getAuthor())
                        .coverImgUrl(book.getCoverImgUrl())
                        .isDefault(book.isDefault())
                        .isFavorite(false) // 관리자 페이지에서는 즐겨찾기 정보가 불필요
                        .updatedAt(book.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * POV 요약본이 없는 챕터 목록을 조회합니다.
     */
    public List<UnsummarizedItemDTO> getUnsummarizedChapters() {
        return chapterRepository.findUnsummarizedChapters().stream()
                .map(UnsummarizedItemDTO::from)
                .collect(Collectors.toList());
    }

    /*
     * =====================================================================================
     * 3. 데이터 삭제
     * =====================================================================================
     */

    /**
     * 특정 책에 속한 모든 등장인물을 삭제합니다.
     */
    @Transactional
    public void deleteCharacters(Long bookId) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));

        if (!characterRepository.existsByBook(book)) {
            throw new GeneralException(ErrorStatus.NO_CHARACTERS_TO_DELETE);
        }
        characterRepository.deleteByBook(book);
    }

    /**
     * 특정 챕터에 속한 모든 이벤트를 삭제합니다.
     */
    @Transactional
    public void deleteEvents(Long bookId, Integer chapterIdx) {
        Chapter chapter = chapterRepository.findByBookIdAndIdx(bookId, chapterIdx)
                .orElseThrow(() -> new GeneralException(ErrorStatus.CHAPTER_NOT_FOUND));

        if (!eventRepository.existsByChapter(chapter)) {
            throw new GeneralException(ErrorStatus.NO_EVENTS_TO_DELETE);
        }
        eventRepository.deleteByChapter(chapter);
    }

    /**
     * 특정 챕터의 모든 POV 요약본을 삭제합니다.
     */
    @Transactional
    public void deleteChapterSummary(Long bookId, Integer chapterIdx) {
        Chapter chapter = chapterRepository.findByBookIdAndIdx(bookId, chapterIdx)
                .orElseThrow(() -> new GeneralException(ErrorStatus.CHAPTER_NOT_FOUND));

        if (!characterPovSummaryRepository.existsByChapter(chapter)) {
            throw new GeneralException(ErrorStatus.NO_SUMMARY_TO_DELETE);
        }
        characterPovSummaryRepository.deleteByChapter(chapter);
        // 챕터의 요약 상태를 '미완료'로 되돌림
        chapter.markPovSummariesAsUncached();
    }

    /**
     * 특정 이벤트에 연결된 모든 관계 정보를 삭제합니다.
     */
    @Transactional
    public void deleteRelationships(Long bookId, Integer chapterIdx, Integer eventIdx) {
        Chapter chapter = chapterRepository.findByBookIdAndIdx(bookId, chapterIdx)
                .orElseThrow(() -> new GeneralException(ErrorStatus.CHAPTER_NOT_FOUND));

        Event event = eventRepository.findByChapterAndIdx(chapter, eventIdx)
                .orElseThrow(() -> new GeneralException(ErrorStatus.EVENT_NOT_FOUND));

        if (!eventRelationshipEdgeRepository.existsByEvent(event)) {
            throw new GeneralException(ErrorStatus.NO_RELATIONSHIPS_TO_DELETE);
        }
        eventRelationshipEdgeRepository.deleteByEvent(event);
    }

    /*
     * =====================================================================================
     * 4. 내부 헬퍼 메서드 및 클래스
     * =====================================================================================
     */

    /**
     * 책에 속한 모든 챕터의 요약이 완료되었는지 확인하고, 책의 전체 요약 상태를 업데이트합니다.
     */
    private void checkAndUpdateBookSummaryStatus(Book book) {
        // 이미 완료된 책은 검사할 필요 없음
        if (book.isSummary()) {
            return;
        }
        List<Chapter> chapters = chapterRepository.findByBookId(book.getId());
        boolean allChaptersSummarized = chapters.stream().allMatch(Chapter::isPovSummariesCached);
        if (allChaptersSummarized) {
            book.completeSummary();
        }
    }

    /**
     * POV 요약본 JSON 파일 파싱을 위한 내부 전용 클래스
     */
    @Getter
    @Setter
    @NoArgsConstructor
    private static class PovSummaryData {
        private String character_name;
        private String summary;
    }
}
