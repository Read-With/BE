package com.kw.readwith.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.Chapter;
import com.kw.readwith.domain.Event;
import com.kw.readwith.domain.Character;
import com.kw.readwith.domain.mapping.EventRelationshipEdge;
import com.kw.readwith.dto.admin.CharacterDTO;
import com.kw.readwith.dto.admin.EventDTO;
import com.kw.readwith.dto.admin.RelationshipDTO;
import com.kw.readwith.dto.admin.RelationshipUploadDTO;
import com.kw.readwith.repository.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private final BookRepository bookRepository;
    private final CharacterRepository characterRepository;
    private final ChapterRepository chapterRepository;
    private final EventRepository eventRepository;
    private final EventRelationshipEdgeRepository eventRelationshipEdgeRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void uploadCharacters(Long bookId, MultipartFile file) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));

        try {
            CharacterDTO.CharacterListDTO characterListDTO = objectMapper.readValue(file.getInputStream(), CharacterDTO.CharacterListDTO.class);

            List<Character> newCharacters = new ArrayList<>();
            for (CharacterDTO dto : characterListDTO.getCharacters()) {
                // snake_case 필드에 맞는 getter 호출
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

            // 데이터베이스에 저장
            if (!newCharacters.isEmpty()) {
                characterRepository.saveAll(newCharacters);
            }

        } catch (IOException e) {
            throw new GeneralException(ErrorStatus.JSON_PARSING_ERROR);
        }
    }

    /**
     * 여러 챕터의 이벤트 JSON 파일들을 한번에 업로드합니다.
     * 파일 이름(chapter<번호>_events.json)에서 챕터 번호를 자동으로 인식하여 처리하며,
     * 트랜잭션으로 동작하여 하나라도 실패 시 모든 작업이 롤백됩니다.
     * @param bookId 이벤트를 추가할 책의 ID
     * @param eventFiles 'chapter<번호>_events.json' 형식의 파일 목록
     */
    @Transactional
    public void uploadEvents(Long bookId, List<MultipartFile> eventFiles) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));

        // DB에 한번에 저장하기 위해 모든 이벤트를 담을 리스트
        List<Event> allNewEvents = new ArrayList<>();
        // 파일명에서 챕터 번호를 추출하기 위한 정규표현식
        Pattern pattern = Pattern.compile("chapter(\\d+)_events\\.json");

        for (MultipartFile eventFile : eventFiles) {
            String filename = eventFile.getOriginalFilename();
            Matcher matcher = pattern.matcher(filename);

            if (!matcher.matches()) {
                throw new GeneralException(ErrorStatus.INVALID_FILE_NAME_FORMAT, "파일명: " + filename);
            }

            // 파일명에서 챕터 번호 추출 및 해당 챕터 조회
            Integer chapterIdx = Integer.parseInt(matcher.group(1));
            Chapter chapter = chapterRepository.findByBookIdAndIdx(bookId, chapterIdx)
                    .orElseThrow(() -> new GeneralException(ErrorStatus.CHAPTER_NOT_FOUND, "챕터 인덱스 " + chapterIdx + "를 찾을 수 없습니다."));

            // 이미 이벤트가 있는 챕터는 업로드 불가
            if (eventRepository.existsByChapter(chapter)) {
                throw new GeneralException(ErrorStatus.EVENT_DATA_ALREADY_EXISTS, "챕터 " + chapterIdx + "의 이벤트는 이미 존재합니다.");
            }

            try {
                // JSON 파일을 DTO 리스트로 파싱
                List<EventDTO> eventDTOs = objectMapper.readValue(eventFile.getInputStream(), new TypeReference<List<EventDTO>>() {});

                // DTO 리스트를 Entity로 변환
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
                // 파일 입출력 또는 JSON 형식 오류 시 예외 발생
                throw new GeneralException(ErrorStatus.JSON_PARSING_ERROR);
            }
        }

        // 모든 파일 처리가 성공하면, 수집된 모든 이벤트를 DB에 한번에 저장
        if (!allNewEvents.isEmpty()) {
            eventRepository.saveAll(allNewEvents);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void uploadRelationships(Long bookId, Integer chapterIdx, Integer eventIdx, MultipartFile file) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));

        Chapter chapter = chapterRepository.findByBookIdAndIdx(book.getId(), chapterIdx)
                .orElseThrow(() -> new GeneralException(ErrorStatus.CHAPTER_NOT_FOUND));

        Event event = eventRepository.findByBookAndChapterAndIdx(book, chapter, eventIdx)
                .orElseThrow(() -> new GeneralException(ErrorStatus.EVENT_NOT_FOUND));

        // 데이터가 이미 존재하는지 확인
        if (eventRelationshipEdgeRepository.existsByEvent(event)) {
            throw new GeneralException(ErrorStatus.RELATIONSHIP_DATA_ALREADY_EXISTS);
        }

        RelationshipUploadDTO uploadDTO;
        try {
            // JSON 파일을 DTO로 파싱
            uploadDTO = objectMapper.readValue(file.getInputStream(), RelationshipUploadDTO.class);
        } catch (IOException e) {
            throw new GeneralException(ErrorStatus.JSON_PARSING_ERROR);
        }

        List<EventRelationshipEdge> newEdges = new ArrayList<>();
        for (RelationshipDTO dto : uploadDTO.getRelations()) {
            // DTO에서 Character 찾아오기
            Character fromChar = characterRepository.findByBookAndCharacterId(book, dto.getId1().longValue())
                    .orElseThrow(() -> new GeneralException(ErrorStatus.CHARACTER_NOT_FOUND, "From Character not found with jsonId: " + dto.getId1()));
            Character toChar = characterRepository.findByBookAndCharacterId(book, dto.getId2().longValue())
                    .orElseThrow(() -> new GeneralException(ErrorStatus.CHARACTER_NOT_FOUND, "To Character not found with jsonId: " + dto.getId2()));

            try {
                // DTO를 Entity로 변환
                EventRelationshipEdge edge = EventRelationshipEdge.builder()
                        .fromCharacter(fromChar)
                        .toCharacter(toChar)
                        .event(event)
                        .edgeWeight(dto.getWeight().floatValue())
                        .sentimentScore(dto.getPositivity().floatValue())
                        .interactionCount(dto.getCount())
                        .relationTags(objectMapper.writeValueAsString(dto.getRelation()))
                        .build();
                newEdges.add(edge);
            } catch (JsonProcessingException e) {
                throw new GeneralException(ErrorStatus.JSON_PARSING_ERROR, "Failed to process relation tags for characters " + dto.getId1() + " and " + dto.getId2());
            }
        }

        // 데이터베이스에 저장
        if (!newEdges.isEmpty()) {
            eventRelationshipEdgeRepository.saveAll(newEdges);
        }
    }
}
