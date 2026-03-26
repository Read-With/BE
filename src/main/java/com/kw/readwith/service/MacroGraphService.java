package com.kw.readwith.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.Character;
import com.kw.readwith.domain.Event;
import com.kw.readwith.domain.mapping.EventCharacterStat;
import com.kw.readwith.domain.mapping.EventRelationshipEdge;
import com.kw.readwith.dto.graph.GraphNodeDTO;
import com.kw.readwith.dto.graph.MacroGraphEdgeDTO;
import com.kw.readwith.dto.graph.MacroGraphResponseDTO;
import com.kw.readwith.repository.BookRepository;
import com.kw.readwith.repository.CharacterRepository;
import com.kw.readwith.repository.EventCharacterStatRepository;
import com.kw.readwith.repository.EventRelationshipEdgeRepository;
import com.kw.readwith.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class MacroGraphService {

    private final BookRepository bookRepository;
    private final EventRepository eventRepository;
    private final EventRelationshipEdgeRepository eventRelationshipEdgeRepository;
    private final EventCharacterStatRepository eventCharacterStatRepository;
    private final CharacterRepository characterRepository;
    private final ObjectMapper objectMapper;
    private final BookAccessPolicy bookAccessPolicy;

    public MacroGraphResponseDTO getMacroGraph(Long bookId, Integer uptoChapter, Long userId) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));
        bookAccessPolicy.ensureReadable(book, userId);

        if (!book.isAnalysisReady()) {
            return MacroGraphResponseDTO.builder()
                    .nodes(List.of())
                    .edges(List.of())
                    .userCurrentChapter(uptoChapter)
                    .build();
        }

        List<Event> lastEvents = eventRepository.findLastEventsByBookAndUptoChapter(book, uptoChapter);
        if (lastEvents.isEmpty()) {
            return MacroGraphResponseDTO.builder()
                    .nodes(List.of())
                    .edges(List.of())
                    .userCurrentChapter(uptoChapter)
                    .build();
        }

        List<EventRelationshipEdge> macroEdges = eventRelationshipEdgeRepository.findByEventIn(lastEvents);
        Set<Long> characterIds = macroEdges.stream()
                .flatMap(edge -> List.of(edge.getFromCharacter().getId(), edge.getToCharacter().getId()).stream())
                .collect(Collectors.toSet());

        List<GraphNodeDTO> nodes = characterRepository.findAllById(characterIds).stream()
                .map(character -> {
                    Event relatedEvent = macroEdges.stream()
                            .filter(edge -> edge.getFromCharacter().getId().equals(character.getId())
                                    || edge.getToCharacter().getId().equals(character.getId()))
                            .map(EventRelationshipEdge::getEvent)
                            .findFirst()
                            .orElse(null);
                    return convertToGraphNodeDTO(character, relatedEvent);
                })
                .toList();

        List<MacroGraphEdgeDTO> edgeDTOs = macroEdges.stream()
                .map(this::convertToMacroGraphEdgeDTO)
                .toList();

        return MacroGraphResponseDTO.builder()
                .nodes(nodes)
                .edges(edgeDTOs)
                .userCurrentChapter(uptoChapter)
                .build();
    }

    private GraphNodeDTO convertToGraphNodeDTO(Character character, Event event) {
        EventCharacterStat characterStat = eventCharacterStatRepository
                .findByEventAndCharacter(event, character)
                .orElse(null);

        Float weight = characterStat != null ? (float) characterStat.getNodeWeight() : null;

        return GraphNodeDTO.builder()
                .id(character.getId())
                .label(character.getName())
                .isMainCharacter(character.isMainCharacter())
                .profileImage(character.getProfileImage())
                .description(truncateText(character.getPersonalityText(), 200))
                .portraitPrompt(character.getProfileText())
                .names(parseNames(character.getNames(), character.getName()))
                .weight(weight)
                .count(null)
                .build();
    }

    private MacroGraphEdgeDTO convertToMacroGraphEdgeDTO(EventRelationshipEdge edge) {
        List<String> relationTags = new ArrayList<>();
        try {
            if (edge.getRelationTags() != null && !edge.getRelationTags().trim().isEmpty()) {
                relationTags = objectMapper.readValue(
                        edge.getRelationTags(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
                );
            }
        } catch (Exception e) {
            log.warn("Failed to parse relationTags: {}", edge.getRelationTags(), e);
        }

        return MacroGraphEdgeDTO.builder()
                .from(edge.getFromCharacter().getId())
                .to(edge.getToCharacter().getId())
                .sentimentScore(edge.getSentimentScore() != null ? edge.getSentimentScore().doubleValue() : 0.0)
                .interactionCount(edge.getInteractionCount())
                .relationTags(relationTags)
                .build();
    }

    private String truncateText(String text, int maxLength) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        String cleanText = text.replaceAll("\\s+", " ").trim();
        if (cleanText.length() <= maxLength) {
            return cleanText;
        }
        return cleanText.substring(0, maxLength - 3) + "...";
    }

    private List<String> parseNames(String names, String fallbackName) {
        if (names == null || names.trim().isEmpty()) {
            return List.of(fallbackName);
        }
        try {
            List<String> nameList = Arrays.stream(names.split(","))
                    .map(String::trim)
                    .filter(name -> !name.isEmpty())
                    .toList();
            return nameList.isEmpty() ? List.of(fallbackName) : nameList;
        } catch (Exception e) {
            log.warn("Failed to parse names: {}, using fallback: {}", names, fallbackName, e);
            return List.of(fallbackName);
        }
    }
}
