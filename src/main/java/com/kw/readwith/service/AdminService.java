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
import com.kw.readwith.domain.enums.SentimentLabel;
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void uploadEvents(Long bookId, Integer chapterIdx, MultipartFile file) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));

        Chapter chapter = chapterRepository.findByBookIdAndIdx(book.getId(), chapterIdx)
                .orElseThrow(() -> new GeneralException(ErrorStatus.CHAPTER_NOT_FOUND));

        if (!chapter.getBook().getId().equals(book.getId())) {
            throw new GeneralException(ErrorStatus.CHAPTER_NOT_BELONG_TO_BOOK);
        }

        if (eventRepository.existsByBookAndChapter(book, chapter)) {
            throw new GeneralException(ErrorStatus.EVENT_DATA_ALREADY_EXISTS);
        }

        try {
            List<EventDTO> eventDTOs = objectMapper.readValue(file.getInputStream(), new TypeReference<List<EventDTO>>() {});

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

            if (!newEvents.isEmpty()) {
                eventRepository.saveAllAndFlush(newEvents);
            }

        } catch (IOException e) {
            throw new GeneralException(ErrorStatus.JSON_PARSING_ERROR);
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

        if (eventRelationshipEdgeRepository.existsByEvent(event)) {
            throw new GeneralException(ErrorStatus.RELATIONSHIP_DATA_ALREADY_EXISTS);
        }

        RelationshipUploadDTO uploadDTO;
        try {
            uploadDTO = objectMapper.readValue(file.getInputStream(), RelationshipUploadDTO.class);
        } catch (IOException e) {
            throw new GeneralException(ErrorStatus.JSON_PARSING_ERROR);
        }

        List<EventRelationshipEdge> newEdges = new ArrayList<>();
        for (RelationshipDTO dto : uploadDTO.getRelations()) {
            Character fromChar = characterRepository.findByBookAndCharacterId(book, dto.getId1().longValue())
                    .orElseThrow(() -> new GeneralException(ErrorStatus.CHARACTER_NOT_FOUND, "From Character not found with jsonId: " + dto.getId1()));
            Character toChar = characterRepository.findByBookAndCharacterId(book, dto.getId2().longValue())
                    .orElseThrow(() -> new GeneralException(ErrorStatus.CHARACTER_NOT_FOUND, "To Character not found with jsonId: " + dto.getId2()));

            try {
                EventRelationshipEdge edge = EventRelationshipEdge.builder()
                        .fromCharacter(fromChar)
                        .toCharacter(toChar)
                        .event(event)
                        .edgeWeight(dto.getWeight().floatValue())
                        .sentimentScore(dto.getPositivity().floatValue())
                        .interactionCount(dto.getCount())
                        .relationTags(objectMapper.writeValueAsString(dto.getRelation()))
                        .sentimentLabel(determineSentimentLabel(dto.getPositivity().floatValue()))
                        .build();
                newEdges.add(edge);
            } catch (JsonProcessingException e) {
                throw new GeneralException(ErrorStatus.JSON_PARSING_ERROR, "Failed to process relation tags for characters " + dto.getId1() + " and " + dto.getId2());
            }
        }

        if (!newEdges.isEmpty()) {
            eventRelationshipEdgeRepository.saveAll(newEdges);
        }
    }

    private SentimentLabel determineSentimentLabel(float score) {
        if (score > 0.3) {
            return SentimentLabel.POS;
        } else if (score < -0.3) {
            return SentimentLabel.NEG;
        } else {
            return SentimentLabel.NEU;
        }
    }
}
