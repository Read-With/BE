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
import com.kw.readwith.dto.common.LocatorDTO;
import com.kw.readwith.dto.graph.GraphNodeDTO;
import com.kw.readwith.dto.relationship.RelationshipDeltaEventDTO;
import com.kw.readwith.dto.relationship.RelationshipDeltaItemDTO;
import com.kw.readwith.dto.relationship.RelationshipDeltaListResponseDTO;
import com.kw.readwith.dto.relationship.RelationshipGraphEdgeDTO;
import com.kw.readwith.dto.relationship.RelationshipGraphResponseDTO;
import com.kw.readwith.dto.relationship.RelationshipNodeWeightDTO;
import com.kw.readwith.repository.BookRepository;
import com.kw.readwith.repository.ChapterRepository;
import com.kw.readwith.repository.EventCharacterStatRepository;
import com.kw.readwith.repository.EventRelationshipEdgeRepository;
import com.kw.readwith.repository.EventRepository;
import com.kw.readwith.util.LocatorSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RelationshipDeltaService {

    private static final String CONTRACT_VERSION = "relationship-delta-v1";

    private final BookRepository bookRepository;
    private final ChapterRepository chapterRepository;
    private final EventRepository eventRepository;
    private final EventRelationshipEdgeRepository eventRelationshipEdgeRepository;
    private final EventCharacterStatRepository eventCharacterStatRepository;
    private final ObjectMapper objectMapper;
    private final BookAccessPolicy bookAccessPolicy;
    private final LocatorSupport locatorSupport;

    public RelationshipDeltaListResponseDTO getRelationshipDeltas(
            Long bookId,
            Integer chapterIndex,
            String eventId,
            String fromEventId,
            String toEventId,
            Long userId
    ) {
        Book book = getReadableBook(bookId, userId);
        List<Event> events = resolveRawDeltaEvents(book, chapterIndex, eventId, fromEventId, toEventId);
        DeltaRows rows = loadDeltaRows(events);

        List<RelationshipDeltaEventDTO> deltas = events.stream()
                .map(event -> toDeltaEvent(event, rows))
                .toList();

        return RelationshipDeltaListResponseDTO.builder()
                .bookId(bookId)
                .deltas(deltas)
                .build();
    }

    public RelationshipGraphResponseDTO getRelationshipGraph(
            Long bookId,
            String scope,
            Integer chapterIndex,
            String eventId,
            Integer blockIndex,
            Integer offset,
            Long userId
    ) {
        Book book = getReadableBook(bookId, userId);
        String resolvedScope = scope == null || scope.isBlank() ? "book" : scope.toLowerCase();
        List<Event> events = resolveGraphEvents(book, resolvedScope, chapterIndex, eventId, blockIndex, offset);
        DeltaRows rows = loadDeltaRows(events);
        FoldResult foldResult = fold(events, rows);

        return RelationshipGraphResponseDTO.builder()
                .bookId(bookId)
                .scope(resolvedScope)
                .chapterIndex(chapterIndex)
                .eventId(eventId)
                .nodes(foldResult.nodes())
                .edges(foldResult.edges())
                .build();
    }

    private Book getReadableBook(Long bookId, Long userId) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));
        bookAccessPolicy.ensureReadable(book, userId);
        return book;
    }

    private List<Event> resolveRawDeltaEvents(Book book, Integer chapterIndex, String eventId, String fromEventId, String toEventId) {
        List<Event> allEvents = eventRepository.findByBookOrderByChapterIdxAscIdxAsc(book);
        if (eventId != null && !eventId.isBlank()) {
            return List.of(findEventByEventId(allEvents, eventId));
        }
        if (fromEventId != null || toEventId != null) {
            if (fromEventId == null || fromEventId.isBlank() || toEventId == null || toEventId.isBlank()) {
                throw new GeneralException(ErrorStatus._BAD_REQUEST, "fromEventId and toEventId must be provided together.");
            }
            return sliceEvents(allEvents, fromEventId, toEventId);
        }
        if (chapterIndex != null) {
            return allEvents.stream()
                    .filter(event -> event.getChapter().getIdx() == chapterIndex)
                    .toList();
        }
        return allEvents;
    }

    private List<Event> resolveGraphEvents(Book book, String scope, Integer chapterIndex, String eventId, Integer blockIndex, Integer offset) {
        List<Event> allEvents = eventRepository.findByBookOrderByChapterIdxAscIdxAsc(book);
        return switch (scope) {
            case "book" -> allEvents;
            case "chapter" -> {
                if (chapterIndex == null) {
                    throw new GeneralException(ErrorStatus._BAD_REQUEST, "chapterIndex is required for chapter scope.");
                }
                yield allEvents.stream()
                        .filter(event -> event.getChapter().getIdx() <= chapterIndex)
                        .toList();
            }
            case "event" -> {
                String requiredEventId = requireText(eventId, "eventId");
                Event target = findEventByEventId(allEvents, requiredEventId);
                int targetIndex = allEvents.indexOf(target);
                yield allEvents.subList(0, targetIndex + 1);
            }
            case "locator" -> {
                if (chapterIndex == null || blockIndex == null || offset == null) {
                    throw new GeneralException(ErrorStatus._BAD_REQUEST, "chapterIndex, blockIndex, offset are required for locator scope.");
                }
                Chapter chapter = chapterRepository.findByBookIdAndIdx(book.getId(), chapterIndex)
                        .orElseThrow(() -> new GeneralException(ErrorStatus.CHAPTER_NOT_FOUND));
                LocatorDTO locator = LocatorDTO.builder()
                        .chapterIndex(chapterIndex)
                        .blockIndex(blockIndex)
                        .offset(offset)
                        .build();
                int txtOffset = locatorSupport.toTxtOffset(chapter, locator);
                yield allEvents.stream()
                        .filter(event -> event.getChapter().getIdx() < chapterIndex
                                || (event.getChapter().getIdx() == chapterIndex && event.getStartTxtOffset() <= txtOffset))
                        .toList();
            }
            default -> throw new GeneralException(ErrorStatus._BAD_REQUEST, "Unsupported relationship graph scope: " + scope);
        };
    }

    private List<Event> sliceEvents(List<Event> allEvents, String fromEventId, String toEventId) {
        Event fromEvent = findEventByEventId(allEvents, fromEventId);
        Event toEvent = findEventByEventId(allEvents, toEventId);
        int fromIndex = allEvents.indexOf(fromEvent);
        int toIndex = allEvents.indexOf(toEvent);
        if (fromIndex > toIndex) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "fromEventId must be before or equal to toEventId.");
        }
        return allEvents.subList(fromIndex, toIndex + 1);
    }

    private Event findEventByEventId(List<Event> events, String eventId) {
        String normalizedEventId = normalizeEventId(eventId);
        return events.stream()
                .filter(event -> normalizedEventId.equals(event.getEventId()))
                .findFirst()
                .orElseThrow(() -> new GeneralException(ErrorStatus.EVENT_NOT_FOUND, "Event not found: " + eventId));
    }

    private String normalizeEventId(String eventId) {
        String value = requireText(eventId, "eventId");
        Matcher matcher = Pattern.compile("^ch(\\d+)-e(\\d+)$").matcher(value);
        if (matcher.matches()) {
            return value;
        }
        throw new GeneralException(ErrorStatus._BAD_REQUEST, "eventId must match ch{chapter}-e{event}: " + eventId);
    }

    private DeltaRows loadDeltaRows(List<Event> events) {
        if (events.isEmpty()) {
            return new DeltaRows(Map.of(), Map.of());
        }

        List<EventRelationshipEdge> edges = eventRelationshipEdgeRepository.findByEventsOrdered(events);
        List<EventCharacterStat> stats = eventCharacterStatRepository.findByEventsOrdered(events);

        Map<Long, List<EventRelationshipEdge>> edgesByEventId = edges.stream()
                .collect(Collectors.groupingBy(edge -> edge.getEvent().getId(), LinkedHashMap::new, Collectors.toList()));
        Map<Long, List<EventCharacterStat>> statsByEventId = stats.stream()
                .collect(Collectors.groupingBy(stat -> stat.getEvent().getId(), LinkedHashMap::new, Collectors.toList()));
        return new DeltaRows(edgesByEventId, statsByEventId);
    }

    private RelationshipDeltaEventDTO toDeltaEvent(Event event, DeltaRows rows) {
        List<RelationshipDeltaItemDTO> items = rows.edgesByEventId()
                .getOrDefault(event.getId(), List.of())
                .stream()
                .map(this::toDeltaItem)
                .toList();

        Map<String, RelationshipNodeWeightDTO> nodeWeights = new LinkedHashMap<>();
        for (EventCharacterStat stat : rows.statsByEventId().getOrDefault(event.getId(), List.of())) {
            nodeWeights.put(
                    String.valueOf(stat.getCharacter().getCharacterId()),
                    RelationshipNodeWeightDTO.builder()
                            .weight(stat.getNodeWeight())
                            .build()
            );
        }

        return RelationshipDeltaEventDTO.builder()
                .contractVersion(CONTRACT_VERSION)
                .chapterIndex(event.getChapter().getIdx())
                .eventId(event.getEventId())
                .items(items)
                .nodeWeights(nodeWeights)
                .build();
    }

    private RelationshipDeltaItemDTO toDeltaItem(EventRelationshipEdge edge) {
        return RelationshipDeltaItemDTO.builder()
                .fromCharacterId(edge.getFromCharacter().getCharacterId())
                .toCharacterId(edge.getToCharacter().getCharacterId())
                .labels(readLabels(edge.getRelationTags()))
                .positivity(edge.getSentimentScore() != null ? edge.getSentimentScore().doubleValue() : null)
                .evidenceCount(edge.getInteractionCount())
                .reason(edge.getExplanation())
                .build();
    }

    private FoldResult fold(List<Event> events, DeltaRows rows) {
        Map<Long, NodeAccumulator> nodeAccumulators = new LinkedHashMap<>();
        Map<String, EdgeAccumulator> edgeAccumulators = new LinkedHashMap<>();

        for (Event event : events) {
            for (EventCharacterStat stat : rows.statsByEventId().getOrDefault(event.getId(), List.of())) {
                nodeAccumulators
                        .computeIfAbsent(stat.getCharacter().getId(), key -> new NodeAccumulator(stat.getCharacter()))
                        .add(stat);
            }

            for (EventRelationshipEdge edge : rows.edgesByEventId().getOrDefault(event.getId(), List.of())) {
                Character from = edge.getFromCharacter();
                Character to = edge.getToCharacter();
                nodeAccumulators.putIfAbsent(from.getId(), new NodeAccumulator(from));
                nodeAccumulators.putIfAbsent(to.getId(), new NodeAccumulator(to));

                String edgeKey = edgeKey(from.getCharacterId(), to.getCharacterId());
                edgeAccumulators
                        .computeIfAbsent(edgeKey, key -> new EdgeAccumulator(from, to))
                        .add(edge);
            }
        }

        List<GraphNodeDTO> nodes = nodeAccumulators.values().stream()
                .map(NodeAccumulator::toDto)
                .toList();
        List<RelationshipGraphEdgeDTO> edges = edgeAccumulators.values().stream()
                .map(EdgeAccumulator::toDto)
                .toList();
        return new FoldResult(nodes, edges);
    }

    private String edgeKey(Long leftCharacterId, Long rightCharacterId) {
        long left = Math.min(leftCharacterId, rightCharacterId);
        long right = Math.max(leftCharacterId, rightCharacterId);
        return left + ":" + right;
    }

    private List<String> readLabels(String relationTags) {
        if (relationTags == null || relationTags.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(
                    relationTags,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
        } catch (Exception e) {
            log.warn("Failed to parse relationTags: {}", relationTags, e);
            return List.of();
        }
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, fieldName + " is required.");
        }
        return value;
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
        List<String> nameList = Arrays.stream(names.split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .toList();
        return nameList.isEmpty() ? List.of(fallbackName) : nameList;
    }

    private record DeltaRows(
            Map<Long, List<EventRelationshipEdge>> edgesByEventId,
            Map<Long, List<EventCharacterStat>> statsByEventId
    ) {
    }

    private record FoldResult(
            List<GraphNodeDTO> nodes,
            List<RelationshipGraphEdgeDTO> edges
    ) {
    }

    private class NodeAccumulator {
        private final Character character;
        private double weightSum;
        private int count;

        private NodeAccumulator(Character character) {
            this.character = character;
        }

        private void add(EventCharacterStat stat) {
            this.weightSum += stat.getNodeWeight();
            this.count++;
        }

        private GraphNodeDTO toDto() {
            return GraphNodeDTO.builder()
                    .id(character.getCharacterId())
                    .label(character.getName())
                    .isMainCharacter(character.isMainCharacter())
                    .profileImage(character.getProfileImage())
                    .description(truncateText(character.getPersonalityText(), 200))
                    .portraitPrompt(character.getProfileText())
                    .names(parseNames(character.getNames(), character.getName()))
                    .weight(count > 0 ? (float) weightSum : null)
                    .count(count > 0 ? count : null)
                    .build();
        }
    }

    private class EdgeAccumulator {
        private final Character left;
        private final Character right;
        private final Map<String, Integer> labelScores = new LinkedHashMap<>();
        private final Map<String, Integer> directionCounts = new LinkedHashMap<>();
        private double positivityWeightedSum;
        private int evidenceCount;
        private List<String> latestLabels = List.of();
        private String latestReason;
        private String latestEventId;

        private EdgeAccumulator(Character from, Character to) {
            if (from.getCharacterId() <= to.getCharacterId()) {
                this.left = from;
                this.right = to;
            } else {
                this.left = to;
                this.right = from;
            }
        }

        private void add(EventRelationshipEdge edge) {
            int deltaCount = edge.getInteractionCount() != null ? edge.getInteractionCount() : 0;
            double positivity = edge.getSentimentScore() != null ? edge.getSentimentScore() : 0.0;
            this.evidenceCount += deltaCount;
            this.positivityWeightedSum += positivity * deltaCount;

            List<String> labels = readLabels(edge.getRelationTags());
            for (String label : labels) {
                labelScores.merge(label, deltaCount, Integer::sum);
            }

            String directionKey = edge.getFromCharacter().getCharacterId() + "->" + edge.getToCharacter().getCharacterId();
            directionCounts.merge(directionKey, deltaCount, Integer::sum);
            latestLabels = labels;
            latestReason = edge.getExplanation();
            latestEventId = edge.getEvent().getEventId();
        }

        private RelationshipGraphEdgeDTO toDto() {
            List<String> labels = labelScores.entrySet().stream()
                    .sorted(Comparator
                            .<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue)
                            .reversed()
                            .thenComparing(Map.Entry::getKey))
                    .limit(3)
                    .map(Map.Entry::getKey)
                    .toList();

            return RelationshipGraphEdgeDTO.builder()
                    .from(left.getCharacterId())
                    .to(right.getCharacterId())
                    .positivity(evidenceCount > 0 ? positivityWeightedSum / evidenceCount : 0.0)
                    .evidenceCount(evidenceCount)
                    .labels(labels)
                    .labelScores(labelScores)
                    .latestLabels(latestLabels)
                    .latestReason(latestReason)
                    .latestEventId(latestEventId)
                    .directionCounts(directionCounts)
                    .build();
        }
    }
}
