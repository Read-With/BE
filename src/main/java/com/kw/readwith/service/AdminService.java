package com.kw.readwith.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.Chapter;
import com.kw.readwith.domain.Character;
import com.kw.readwith.domain.Event;
import com.kw.readwith.dto.admin.CharacterDTO;
import com.kw.readwith.dto.admin.EventDTO;
import com.kw.readwith.repository.BookRepository;
import com.kw.readwith.repository.ChapterRepository;
import com.kw.readwith.repository.CharacterRepository;
import com.kw.readwith.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminService {

    private final BookRepository bookRepository;
    private final CharacterRepository characterRepository;
    private final ChapterRepository chapterRepository;
    private final EventRepository eventRepository;
    private final ObjectMapper objectMapper;

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

            if (!newCharacters.isEmpty()) {
                characterRepository.saveAll(newCharacters);
            }

        } catch (IOException e) {
            throw new GeneralException(ErrorStatus.JSON_PARSING_ERROR);
        }
    }

    public void uploadEvents(Long bookId, Integer chapterIdx, MultipartFile file) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));

        // Repository의 기존 메소드에 맞게 book.getId()와 chapterIdx를 전달
        Chapter chapter = chapterRepository.findByBookIdAndIdx(book.getId(), chapterIdx)
                .orElseThrow(() -> new GeneralException(ErrorStatus.CHAPTER_NOT_FOUND));

        // 해당 책에 속한 챕터가 맞는지 확인
        if (!chapter.getBook().getId().equals(book.getId())) {
            throw new GeneralException(ErrorStatus.CHAPTER_NOT_BELONG_TO_BOOK);
        }

        // Book과 Chapter를 함께 사용하여 데이터가 이미 존재하는지 확인
        if (eventRepository.existsByBookAndChapter(book, chapter)) {
            throw new GeneralException(ErrorStatus.EVENT_DATA_ALREADY_EXISTS);
        }

        try {
            // JSON 파일을 DTO 리스트로 파싱
            List<EventDTO> eventDTOs = objectMapper.readValue(file.getInputStream(), new TypeReference<List<EventDTO>>() {});

            // DTO를 Entity로 변환
            List<Event> newEvents = eventDTOs.stream()
                    .map(dto -> Event.builder()
                            .startPos(dto.getStart())
                            .endPos(dto.getEnd())
                            .rawText(dto.getText())
                            .idx(dto.getEventId())
                            .chapter(chapter)
                            .book(book)
                            .build())
                    .collect(Collectors.toList());

            // 데이터베이스에 저장
            if (!newEvents.isEmpty()) {
                eventRepository.saveAll(newEvents);
            }

        } catch (IOException e) {
            throw new GeneralException(ErrorStatus.JSON_PARSING_ERROR);
        }
    }
}