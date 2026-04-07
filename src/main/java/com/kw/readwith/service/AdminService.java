package com.kw.readwith.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.config.V2TransitionGuard;
import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.Chapter;
import com.kw.readwith.domain.Character;
import com.kw.readwith.domain.Event;
import com.kw.readwith.domain.enums.ImageGenerationStatus;
import com.kw.readwith.domain.mapping.CharacterPovSummary;
import com.kw.readwith.domain.mapping.EventCharacterStat;
import com.kw.readwith.domain.mapping.EventRelationshipEdge;
import com.kw.readwith.dto.admin.*;
import com.kw.readwith.dto.book.BookSummaryDTO;
import com.kw.readwith.dto.common.LocatorDTO;
import com.kw.readwith.repository.*;
import com.kw.readwith.service.normalization.NormalizedArtifactStorageService;
import com.kw.readwith.service.normalization.NormalizationVersionService;
import com.kw.readwith.util.LocatorSupport;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // 疫꿸퀡??怨몄몵嚥???꾨┛ ?袁⑹뒠 ?紐껋삏?????곗쨮 ??쇱젟
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    // 筌뤴뫀諭??온?귐딆쁽 疫꿸퀡????袁⑹뒄??Repository 獄?ObjectMapper ??뤵??雅뚯눘??
    private final BookRepository bookRepository;
    private final CharacterRepository characterRepository;
    private final ChapterRepository chapterRepository;
    private final EventRepository eventRepository;
    private final EventRelationshipEdgeRepository eventRelationshipEdgeRepository;
    private final CharacterPovSummaryRepository characterPovSummaryRepository;
    private final EventCharacterStatRepository statRepository;
    private final ObjectMapper objectMapper;
    private final CharacterImageService characterImageService;
    private final BookAnalysisStatusService bookAnalysisStatusService;
    private final LocatorSupport locatorSupport;
    private final V2TransitionGuard transitionGuard;
    private final NormalizationVersionService normalizationVersionService;
    private final NormalizedArtifactStorageService normalizedArtifactStorageService;
    
    @PersistenceContext
    private EntityManager entityManager;

    /*
     * =====================================================================================
     * 1. ?怨쀬뵠????낆쨮??
     * =====================================================================================
     */

    /**
     * ?源놁삢?紐꺪??類ｋ궖揶쎛 ??용┸ JSON ???뵬????낆쨮??쀫???덈뼄.
     */
    @Transactional
    public int uploadCharacters(Long bookId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "Upload file is empty.");
        }

        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));

        try {
            CharacterListDTO characterListDTO = objectMapper.readValue(file.getInputStream(), CharacterListDTO.class);
            if (characterListDTO == null || characterListDTO.getItems() == null) {
                throw new GeneralException(ErrorStatus._BAD_REQUEST, "Character payload is invalid.");
            }

            String bookPrompt = normalizeOptionalText(characterListDTO.getBookPrompt());
            if (bookPrompt != null) {
                book.updateBookPrompt(bookPrompt);
            }

            if (characterListDTO.getItems().isEmpty()) {
                log.warn("No characters found in upload payload.");
                bookAnalysisStatusService.refreshStatus(bookId);
                return 0;
            }

            List<Character> newCharacters = new ArrayList<>();
            for (CharacterDTO dto : characterListDTO.getItems()) {
                String commonName = requireText(dto.getCommonName(), "character.commonName");
                Long characterId = parseCharacterId(dto.getCharacterId(), "character.characterId");
                String descriptionKo = requireText(resolveCharacterDescription(dto), "character.descriptions.ko");
                String portraitPrompt = requireText(dto.getPortraitPrompt(), "character.portraitPrompt");

                Optional<Character> existingCharacter = characterRepository.findByBookAndCharacterId(book, characterId);
                if (existingCharacter.isPresent()) {
                    log.info("Skip existing character id={}", characterId);
                    continue;
                }

                Character character = Character.builder()
                        .book(book)
                        .characterId(characterId)
                        .name(commonName)
                        .names(joinNames(dto.getNames(), commonName))
                        .isMainCharacter(dto.isMainCharacter())
                        .firstChapterIdx(1)
                        .personalityText(descriptionKo)
                        .profileText(portraitPrompt)
                        .imageGenerationStatus(ImageGenerationStatus.PENDING)
                        .build();
                newCharacters.add(character);
            }

            if (newCharacters.isEmpty()) {
                log.info("No new characters to save.");
                bookAnalysisStatusService.refreshStatus(bookId);
                return 0;
            }

            List<Character> savedCharacters;
            try {
                savedCharacters = characterRepository.saveAll(newCharacters);
                log.info("Saved {} characters.", savedCharacters.size());
            } catch (Exception e) {
                log.error("Failed to save characters.", e);
                throw new GeneralException(ErrorStatus._INTERNAL_SERVER_ERROR, "Failed to save characters.");
            }

            List<Long> characterIds = savedCharacters.stream()
                    .map(Character::getId)
                    .collect(Collectors.toList());

            try {
                characterImageService.generateImagesAsync(characterIds);
            } catch (Exception e) {
                log.error("Failed to start character image generation.", e);
            }

            bookAnalysisStatusService.refreshStatus(bookId);
            return savedCharacters.size();
        } catch (IOException e) {
            log.error("Failed to parse character upload JSON.", e);
            GeneralException exception = new GeneralException(ErrorStatus.JSON_PARSING_ERROR, "Invalid character JSON payload.");
            markAnalysisRejectedIfNeeded(book, exception);
            throw exception;
        } catch (GeneralException e) {
            markAnalysisRejectedIfNeeded(book, e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected failure while uploading characters.", e);
            throw new GeneralException(ErrorStatus._INTERNAL_SERVER_ERROR, "Failed to upload characters.");
        }
    }

    /**
     * ????筌?벤苑????源??JSON ???뵬??쇱뱽 ??뺤쓰????낆쨮??쀫???덈뼄.
     */
    @Transactional
    public int uploadEvents(Long bookId, List<MultipartFile> eventFiles) {
        if (eventFiles == null || eventFiles.isEmpty()) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "No event files provided.");
        }

        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));
        transitionGuard.ensureLocatorWritesEnabled("admin event upload");

        try {
            List<Event> allNewEvents = new ArrayList<>();
            Map<Integer, String> chapterTextCache = new HashMap<>();

            for (MultipartFile eventFile : eventFiles) {
                if (eventFile == null || eventFile.isEmpty()) {
                    log.warn("Skip empty event file.");
                    continue;
                }

                try {
                    EventUploadDTO uploadDTO = objectMapper.readValue(eventFile.getInputStream(), EventUploadDTO.class);
                    if (uploadDTO == null || uploadDTO.getChapterIndex() == null) {
                        throw new GeneralException(ErrorStatus._BAD_REQUEST, "event payload chapterIndex is required.");
                    }

                    Integer chapterIdx = uploadDTO.getChapterIndex();
                    Chapter chapter = chapterRepository.findByBookIdAndIdx(bookId, chapterIdx)
                            .orElseThrow(() -> new GeneralException(ErrorStatus.CHAPTER_NOT_FOUND, "Chapter not found: " + chapterIdx));
                    transitionGuard.ensureLocatorMetadataReady(book, chapter, "admin event upload");

                    if (eventRepository.existsByChapter(chapter)) {
                        throw new GeneralException(ErrorStatus.EVENT_DATA_ALREADY_EXISTS, "Events already exist for chapter " + chapterIdx);
                    }

                    List<EventDTO> eventDTOs = uploadDTO.getItems();
                    if (eventDTOs == null || eventDTOs.isEmpty()) {
                        log.warn("No events found in file {}", eventFile.getOriginalFilename());
                        continue;
                    }
                    String chapterText = chapterTextCache.computeIfAbsent(
                            chapterIdx,
                            idx -> loadNormalizedChapterText(book, idx)
                    );

                    Set<Integer> seenEventIndexes = new LinkedHashSet<>();
                    List<Event> newEventsFromFile = new ArrayList<>();
                    for (EventDTO dto : eventDTOs) {
                        String eventId = requireText(dto.getEventId(), "event.eventId");
                        if (dto.getChapterIndex() != null && !chapterIdx.equals(dto.getChapterIndex())) {
                            throw new GeneralException(ErrorStatus._BAD_REQUEST, "Root chapterIndex and item chapterIndex do not match.");
                        }

                        Integer startTxtOffset = dto.getStartTxtOffset() != null ? dto.getStartTxtOffset() : dto.getStart();
                        Integer endTxtOffset = dto.getEndTxtOffset() != null ? dto.getEndTxtOffset() : dto.getEnd();
                        if (startTxtOffset == null || endTxtOffset == null) {
                            throw new GeneralException(ErrorStatus._BAD_REQUEST, "Missing txt offsets for event " + eventId);
                        }
                        if (startTxtOffset >= endTxtOffset) {
                            throw new GeneralException(ErrorStatus._BAD_REQUEST, "Invalid txt offset range for event " + eventId);
                        }

                        String rawText = requireRawText(dto.getEventText() != null ? dto.getEventText() : dto.getText(), "event.eventText");
                        validateEventText(chapterText, startTxtOffset, endTxtOffset, rawText, eventId);

                        Integer eventIdx = parseEventIdx(eventId, chapterIdx);
                        if (!seenEventIndexes.add(eventIdx)) {
                            throw new GeneralException(ErrorStatus._BAD_REQUEST, "Duplicate eventId in payload: " + eventId);
                        }

                        String normalizedEventId = normalizeEventId(eventId, chapterIdx, eventIdx);
                        LocatorDTO startLocator = resolveEventLocator(chapter, startTxtOffset);
                        LocatorDTO endLocator = resolveEventLocator(chapter, endTxtOffset);

                        Event event = Event.builder()
                                .book(book)
                                .chapter(chapter)
                                .idx(eventIdx)
                                .eventId(normalizedEventId)
                                .startBlockIndex(startLocator != null ? startLocator.getBlockIndex() : null)
                                .startOffset(startLocator != null ? startLocator.getOffset() : null)
                                .endBlockIndex(endLocator != null ? endLocator.getBlockIndex() : null)
                                .endOffset(endLocator != null ? endLocator.getOffset() : null)
                                .startTxtOffset(startTxtOffset)
                                .endTxtOffset(endTxtOffset)
                                .rawText(rawText)
                                .build();
                        newEventsFromFile.add(event);
                    }

                    allNewEvents.addAll(newEventsFromFile);
                    log.info("Parsed {} events from {}", newEventsFromFile.size(), eventFile.getOriginalFilename());
                } catch (IOException e) {
                    log.error("Failed to parse event file {}", eventFile.getOriginalFilename(), e);
                    throw new GeneralException(ErrorStatus.JSON_PARSING_ERROR, "Failed to parse event JSON: " + eventFile.getOriginalFilename());
                }
            }

            if (allNewEvents.isEmpty()) {
                log.warn("No new events to save.");
                bookAnalysisStatusService.refreshStatus(bookId);
                return 0;
            }

            int batchSize = 500;
            int totalSaved = 0;
            try {
                for (int i = 0; i < allNewEvents.size(); i += batchSize) {
                    int end = Math.min(i + batchSize, allNewEvents.size());
                    List<Event> batch = allNewEvents.subList(i, end);
                    eventRepository.saveAll(batch);
                    entityManager.flush();
                    entityManager.clear();
                    totalSaved += batch.size();
                }
                log.info("Saved {} events.", totalSaved);
            } catch (Exception e) {
                log.error("Failed to save events.", e);
                throw new GeneralException(ErrorStatus._INTERNAL_SERVER_ERROR, "Failed to save events.");
            }

            bookAnalysisStatusService.refreshStatus(bookId);
            return totalSaved;
        } catch (GeneralException e) {
            markAnalysisRejectedIfNeeded(book, e);
            throw e;
        }
    }

    /**
     * ????筌?벤苑??POV ?遺용튋 JSON ???뵬??쇱뱽 ??뺤쓰????낆쨮??쀫???덈뼄.
     */
    @Transactional
    public int uploadChapterSummaries(Long bookId, List<MultipartFile> summaryFiles) {
        if (summaryFiles == null || summaryFiles.isEmpty()) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "No summary files provided.");
        }

        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));

        try {
            List<CharacterPovSummary> allNewSummaries = new ArrayList<>();

            for (MultipartFile summaryFile : summaryFiles) {
                if (summaryFile == null || summaryFile.isEmpty()) {
                    log.warn("Skip empty summary file.");
                    continue;
                }

                SummaryUploadDTO uploadDTO;
                try {
                    uploadDTO = objectMapper.readValue(summaryFile.getInputStream(), SummaryUploadDTO.class);
                } catch (IOException e) {
                    throw new GeneralException(ErrorStatus.JSON_PARSING_ERROR, "Failed to parse summary JSON.");
                }

                if (uploadDTO == null || uploadDTO.getChapterIndex() == null) {
                    throw new GeneralException(ErrorStatus._BAD_REQUEST, "summary payload chapterIndex is required.");
                }
                if (uploadDTO.getLanguage() != null && !"ko".equalsIgnoreCase(uploadDTO.getLanguage())) {
                    log.info("Skip non-ko summary file {} language={}", summaryFile.getOriginalFilename(), uploadDTO.getLanguage());
                    continue;
                }

                Integer chapterIdx = uploadDTO.getChapterIndex();
                Chapter chapter = chapterRepository.findByBookIdAndIdx(bookId, chapterIdx)
                        .orElseThrow(() -> new GeneralException(ErrorStatus.CHAPTER_NOT_FOUND, "Chapter not found: " + chapterIdx));

                if (characterPovSummaryRepository.existsByChapter(chapter)) {
                    throw new GeneralException(ErrorStatus.CHAPTER_ALREADY_SUMMARIZED, "Chapter summary already exists: " + chapterIdx);
                }

                List<SummaryItemDTO> summaryItems = requireSummaryItems(uploadDTO);
                List<CharacterPovSummary> newSummariesFromFile = summaryItems.stream()
                        .map(this::validateSummaryItem)
                        .map(item -> buildCharacterPovSummary(chapter, item))
                        .collect(Collectors.toList());
                allNewSummaries.addAll(newSummariesFromFile);

                chapter.markAsSummarized();
            }

            if (!allNewSummaries.isEmpty()) {
                try {
                    characterPovSummaryRepository.saveAll(allNewSummaries);
                    log.info("Saved {} chapter POV summaries.", allNewSummaries.size());
                } catch (Exception e) {
                    log.error("Failed to save chapter summaries.", e);
                    throw new GeneralException(ErrorStatus._INTERNAL_SERVER_ERROR, "Failed to save chapter summaries.");
                }
            }

            try {
                checkAndUpdateBookSummaryStatus(book);
            } catch (Exception e) {
                log.error("Failed to update legacy summary flag.", e);
            }

            bookAnalysisStatusService.refreshStatus(bookId);
            return allNewSummaries.size();
        } catch (GeneralException e) {
            markAnalysisRejectedIfNeeded(book, e);
            throw e;
        }
    }

    /**
     * ?????온??JSON ???뵬????낆쨮??쀫릭??DB??????
     * ???뵬 ??已?域뱀뮇?? chapter<甕곕뜇??_..._event_<甕곕뜇??.json
     *
     * @param bookId ?온???類ｋ궖???곕떽???筌?굞??ID
     * @param files  ??낆쨮??쀫막 ?온???類ｋ궖 JSON ???뵬 筌뤴뫖以?
     */
    @Transactional
    public int uploadRelationships(Long bookId, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "No relationship files provided.");
        }

        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));

        try {
            int totalProcessedCount = 0;

            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) {
                    log.warn("Skip empty relationship file.");
                    continue;
                }

                try {
                    RelationshipUploadDTO dto = objectMapper.readValue(file.getInputStream(), RelationshipUploadDTO.class);
                    if (dto == null || dto.getChapterIndex() == null) {
                        throw new GeneralException(ErrorStatus._BAD_REQUEST, "relationship payload chapterIndex is required.");
                    }

                    String eventId = requireText(dto.getEventId(), "relationship.eventId");
                    int chapterIdx = dto.getChapterIndex();
                    int eventIdx = parseEventIdx(eventId, chapterIdx);

                    Event event = eventRepository.findByBookIdAndChapterIdxAndEventIdx(bookId, chapterIdx, eventIdx)
                            .orElseThrow(() -> new GeneralException(
                                    ErrorStatus.EVENT_NOT_FOUND,
                                    String.format("Event not found for book=%d chapter=%d event=%d", bookId, chapterIdx, eventIdx)
                            ));

                    totalProcessedCount += processNodeWeights(event, book, dto.getNodeWeights());
                    totalProcessedCount += processRelations(event, book, dto.getItems());
                } catch (IOException e) {
                    throw new GeneralException(ErrorStatus.JSON_PARSING_ERROR, "Failed to parse relationship JSON: " + file.getOriginalFilename());
                }
            }

            bookAnalysisStatusService.refreshStatus(bookId);
            return totalProcessedCount;
        } catch (GeneralException e) {
            markAnalysisRejectedIfNeeded(book, e);
            throw e;
        }
    }

    private SummaryItemDTO validateSummaryItem(SummaryItemDTO item) {
        requireText(item.getCharacterId(), "summary.characterId");
        requireText(item.getSummary(), "summary.summary");
        return item;
    }

    private List<SummaryItemDTO> requireSummaryItems(SummaryUploadDTO uploadDTO) {
        if (uploadDTO.getItems() == null || uploadDTO.getItems().isEmpty()) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "summary.items must contain at least one item.");
        }
        return uploadDTO.getItems();
    }

    private CharacterPovSummary buildCharacterPovSummary(Chapter chapter, SummaryItemDTO item) {
        Long characterId = parseCharacterId(item.getCharacterId(), "summary.characterId");
        Character character = characterRepository.findByBookAndCharacterId(chapter.getBook(), characterId)
                .orElseThrow(() -> new GeneralException(
                        ErrorStatus.CHARACTER_NOT_FOUND,
                        "Character not found with jsonId: " + characterId
                ));

        return CharacterPovSummary.builder()
                .book(chapter.getBook())
                .chapter(chapter)
                .character(character)
                .summaryText(requireText(item.getSummary(), "summary.summary"))
                .build();
    }

    private int processNodeWeights(Event event, Book book, Map<String, NodeWeightDTO> nodeWeightsMap) {
        if (nodeWeightsMap == null || nodeWeightsMap.isEmpty()) {
            return 0;
        }

        List<EventCharacterStat> newStats = new ArrayList<>();
        for (Map.Entry<String, NodeWeightDTO> entry : nodeWeightsMap.entrySet()) {
            Long characterBookId = parseCharacterId(entry.getKey(), "relationship.nodeWeights.characterId");
            NodeWeightDTO weightDTO = entry.getValue();
            if (weightDTO == null) {
                throw new GeneralException(ErrorStatus._BAD_REQUEST, "nodeWeights payload揶쎛 ??쑴堉???됰뮸??덈뼄: " + characterBookId);
            }

            Character character = characterRepository.findByBookAndCharacterId(book, characterBookId)
                    .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_CHARACTER_NOT_FOUND,
                            "????筌?굞肉??源낆쨯??筌?Ŧ??怨? 筌≪뼚??????곷뮸??덈뼄: " + characterBookId));

            if (statRepository.findByEventAndCharacter(event, character).isPresent()) {
                throw new GeneralException(ErrorStatus.NODE_WEIGHT_ALREADY_EXISTS,
                        String.format("??????源?紐꾨퓠 ????'%s' 筌?Ŧ??怨쀬벥 揶쎛餓λ쵐???怨쀬뵠?怨? ??? 鈺곕똻???몃빍??", character.getName()));
            }

            EventCharacterStat stat = EventCharacterStat.builder()
                    .event(event)
                    .character(character)
                    .nodeWeight(weightDTO.getWeight())
                    .build();

            log.info("Character {} weight: {}, count: {} (count not stored in EventCharacterStat)",
                    character.getName(), weightDTO.getWeight(), weightDTO.getCount());
            newStats.add(stat);
        }

        if (newStats.isEmpty()) {
            log.warn("???館釉??紐껊굡 揶쎛餓λ쵐?귛첎? ??곷뮸??덈뼄.");
            return 0;
        }

        try {
            statRepository.saveAll(newStats);
            log.info("?紐껊굡 揶쎛餓λ쵐??{}揶?DB ?????袁⑥┷", newStats.size());
        } catch (Exception e) {
            log.error("?紐껊굡 揶쎛餓λ쵐??DB ??????쎈솭", e);
            throw new GeneralException(ErrorStatus._INTERNAL_SERVER_ERROR, "?紐껊굡 揶쎛餓λ쵐??????餓??怨쀬뵠?怨뺤퓢??곷뮞 ??살첒揶쎛 獄쏆뮇源??됰뮸??덈뼄.");
        }
        return newStats.size();
    }

    /**
     * JSON??'relations' ?봔?브쑴??筌ｌ꼶???롫뮉 ????筌롫뗄苑??
     * ?紐꺪??온?④쑬? ???館釉?묾??? 餓λ쵎???怨쀬뵠?怨? ??덈뮉筌왖 ?類ㅼ뵥
     *
     * @param event        ?袁⑹삺 筌ｌ꼶??餓λ쵐????源???酉苑??
     * @param book         ?袁⑹삺 筌ｌ꼶??餓λ쵐??筌??酉苑??
     * @param relationDTOs JSON?癒?퐣 ???뼓???온???類ｋ궖 DTO 筌뤴뫖以?
     */
    private int processRelations(Event event, Book book, List<RelationshipDTO> relationDTOs) {
        if (relationDTOs == null || relationDTOs.isEmpty()) {
            return 0;
        }

        List<EventRelationshipEdge> newEdges = new ArrayList<>();
        for (RelationshipDTO dto : relationDTOs) {
            Long fromCharacterId = parseCharacterId(dto.getFromCharacterId(), "relationship.fromCharacterId");
            Long toCharacterId = parseCharacterId(dto.getToCharacterId(), "relationship.toCharacterId");

            if (fromCharacterId.equals(toCharacterId)) {
                continue;
            }
            if (dto.getPositivity() == null || dto.getEvidenceCount() == null) {
                throw new GeneralException(ErrorStatus._BAD_REQUEST, "relationship positivity/evidenceCount???袁⑸땾??낅빍??");
            }

            Character fromChar = characterRepository.findByBookAndCharacterId(book, fromCharacterId)
                    .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_CHARACTER_NOT_FOUND,
                            "????筌?굞肉??源낆쨯??筌?Ŧ??怨? 筌≪뼚??????곷뮸??덈뼄: " + fromCharacterId));
            Character toChar = characterRepository.findByBookAndCharacterId(book, toCharacterId)
                    .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_CHARACTER_NOT_FOUND,
                            "????筌?굞肉??源낆쨯??筌?Ŧ??怨? 筌≪뼚??????곷뮸??덈뼄: " + toCharacterId));

            if (eventRelationshipEdgeRepository.existsByEventAndFromCharacterAndToCharacter(event, fromChar, toChar) ||
                    eventRelationshipEdgeRepository.existsByEventAndFromCharacterAndToCharacter(event, toChar, fromChar)) {
                throw new GeneralException(ErrorStatus.RELATIONSHIP_DATA_ALREADY_EXISTS,
                        String.format("??????源?紐꾨퓠 ????'%s'??(?? '%s'???온???怨쀬뵠?怨? ??? 鈺곕똻???몃빍??", fromChar.getName(), toChar.getName()));
            }

            try {
                EventRelationshipEdge edge = EventRelationshipEdge.builder()
                        .event(event)
                        .fromCharacter(fromChar)
                        .toCharacter(toChar)
                        .sentimentScore(dto.getPositivity().floatValue())
                        .interactionCount(dto.getEvidenceCount())
                        .relationTags(objectMapper.writeValueAsString(dto.getLabels() != null ? dto.getLabels() : List.of()))
                        .build();
                newEdges.add(edge);
            } catch (JsonProcessingException e) {
                throw new GeneralException(ErrorStatus.JSON_PARSING_ERROR,
                        String.format("筌?Ŧ???%d??(?? %d???온????볥젃 筌ｌ꼶??餓???살첒揶쎛 獄쏆뮇源??됰뮸??덈뼄.", fromCharacterId, toCharacterId));
            }
        }

        if (newEdges.isEmpty()) {
            log.warn("???館釉??온???節?揶쎛 ??곷뮸??덈뼄.");
            return 0;
        }

        try {
            eventRelationshipEdgeRepository.saveAll(newEdges);
            log.info("?온???節? {}揶?DB ?????袁⑥┷", newEdges.size());
        } catch (Exception e) {
            log.error("?온???節? DB ??????쎈솭", e);
            throw new GeneralException(ErrorStatus._INTERNAL_SERVER_ERROR, "?온???怨쀬뵠??????餓??怨쀬뵠?怨뺤퓢??곷뮞 ??살첒揶쎛 獄쏆뮇源??됰뮸??덈뼄.");
        }
        return newEdges.size();
    }

    /*
     * =====================================================================================
     * 2. ?怨쀬뵠??鈺곌퀬??
     * =====================================================================================
     */

    /**
     * ?袁⑷퍥 ?遺용튋???袁⑥┷??? ??? 筌?筌뤴뫖以??鈺곌퀬???몃빍??
     */
    public List<BookSummaryDTO> getUnsummarizedBooks() {
        List<Book> books = bookRepository.findBySummaryIsFalse().stream()
                .filter(Book::isNormalizationReady)
                .toList();
        return books.stream()
                .map(book -> BookSummaryDTO.builder()
                        .id(book.getId())
                        .title(book.getTitle())
                        .author(book.getAuthor())
                        .coverImgUrl(book.getCoverImgUrl())
                        .epubPath(book.getEpubPath())
                        .normalizationStatus(book.getNormalizationStatus() != null ? book.getNormalizationStatus().name() : null)
                        .analysisStatus(book.getAnalysisStatus() != null ? book.getAnalysisStatus().name() : null)
                        .ruleVersion(book.getRuleVersion())
                        .locatorVersion(book.getLocatorVersion())
                        .normalizationRunId(book.getNormalizationRunId())
                        .normalizationVersionStatus(normalizationVersionService.resolveStatus(book).name())
                        .needsRenormalization(normalizationVersionService.needsRenormalization(book))
                        .normalizedArtifactPath(book.getNormalizedArtifactPath())
                        .isDefault(book.isDefault())
                        .isFavorite(false) // ?온?귐딆쁽 ??륁뵠筌왖?癒?퐣??筌앸Þ爰쇽㎕?섎┛ ?類ｋ궖揶쎛 ?븍뜇釉??
                        .summary(book.isSummary())
                        .updatedAt(book.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * POV ?遺용튋癰귣챷????용뮉 筌?벤苑?筌뤴뫖以??鈺곌퀬???몃빍??
     */
    public List<UnsummarizedItemDTO> getUnsummarizedChapters() {
        return chapterRepository.findUnsummarizedChapters().stream()
                .map(UnsummarizedItemDTO::from)
                .collect(Collectors.toList());
    }

    /**
     * 특정 도서에 속한 캐릭터들의 기본 정보와 이미지 생성 상태를 조회합니다.
     */
    public List<CharacterDTO> getCharactersByBookId(Long bookId) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));

        return characterRepository.findByBook(book).stream()
                .map(character -> CharacterDTO.builder()
                        .id(character.getId())
                        .characterId(String.valueOf(character.getCharacterId()))
                        .commonName(character.getName())
                        .profileImage(character.getProfileImage())
                        .imageGenerationStatus(character.getImageGenerationStatus() != null ? character.getImageGenerationStatus().name() : null)
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * ?諭??筌?Ŧ??怨쀬벥 ?袁⑥쨮?????筌왖????源?源딅???덈뼄.
     * ???筌왖 ??밴쉐????쎈솭??뉕탢????됱춳???ル뿭? ??? 野껋럩???????몃빍??
     * 
     * @param characterId ???筌왖????源?源딅막 筌?Ŧ???ID
     */
    @Transactional
    public void regenerateCharacterImage(Long characterId) {
        // 筌?Ŧ???鈺곕똻????? ?類ㅼ뵥
        Character character = characterRepository.findById(characterId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.CHARACTER_NOT_FOUND, 
                        "筌?Ŧ??怨? 筌≪뼚??????곷뮸??덈뼄: " + characterId));
        
        log.info("筌?Ŧ???'{}' (ID: {}) ???筌왖 ??源????뽰삂", character.getName(), characterId);
        
        // 疫꿸퀣?????筌왖 ?怨밴묶 嚥≪뮄??
        if (character.getProfileImage() != null && !character.getProfileImage().isEmpty()) {
            log.info("疫꿸퀣?????筌왖 URL: {}, ?怨밴묶: {}", 
                    character.getProfileImage(), character.getImageGenerationStatus());
        } else {
            log.info("疫꿸퀣?????筌왖 ??곸벉, ?怨밴묶: {}", character.getImageGenerationStatus());
        }
        
        // ???筌왖 ??源??(??쑬猷욄묾怨? ?袁⑤빒 ??녿┛ 獄쎻뫗???곗쨮 ?紐꾪뀱)
        try {
            characterImageService.generateAndSaveImage(characterId);
            log.info("筌?Ŧ???'{}' (ID: {}) ???筌왖 ??源???袁⑥┷", character.getName(), characterId);
        } catch (Exception e) {
            log.error("筌?Ŧ???'{}' (ID: {}) ???筌왖 ??源????쎈솭", character.getName(), characterId, e);
            throw new GeneralException(ErrorStatus._INTERNAL_SERVER_ERROR, 
                    "???筌왖 ??源??餓???살첒揶쎛 獄쏆뮇源??됰뮸??덈뼄: " + e.getMessage());
        }
    }

    /*
     * =====================================================================================
     * 3. ?怨쀬뵠??????
     * =====================================================================================
     */

    /**
     * ?諭??筌?굞肉???곷립 筌뤴뫀諭??源놁삢?紐꺪???????몃빍??
     */
    @Transactional
    public int deleteCharacters(Long bookId) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));

        if (!characterRepository.existsByBook(book)) {
            throw new GeneralException(ErrorStatus.NO_CHARACTERS_TO_DELETE);
        }
        int deletedCount = characterRepository.deleteByBook(book);
        bookAnalysisStatusService.resetToNone(bookId);
        return deletedCount;
    }

    /**
     * ?諭??筌?벤苑????곷립 筌뤴뫀諭???源?紐? ?????몃빍??
     */
    @Transactional
    public int deleteEvents(Long bookId, Integer chapterIdx) {
        Chapter chapter = chapterRepository.findByBookIdAndIdx(bookId, chapterIdx)
                .orElseThrow(() -> new GeneralException(ErrorStatus.CHAPTER_NOT_FOUND));

        if (!eventRepository.existsByChapter(chapter)) {
            throw new GeneralException(ErrorStatus.NO_EVENTS_TO_DELETE);
        }
        int deletedCount = eventRepository.deleteByChapter(chapter);
        bookAnalysisStatusService.resetToNone(bookId);
        return deletedCount;
    }

    /**
     * ?諭??筌?벤苑??筌뤴뫀諭?POV ?遺용튋癰귣챷???????몃빍??
     */
    @Transactional
    public int deleteChapterSummary(Long bookId, Integer chapterIdx) {
        Chapter chapter = chapterRepository.findByBookIdAndIdx(bookId, chapterIdx)
                .orElseThrow(() -> new GeneralException(ErrorStatus.CHAPTER_NOT_FOUND));

        if (!characterPovSummaryRepository.existsByChapter(chapter)) {
            throw new GeneralException(ErrorStatus.NO_SUMMARY_TO_DELETE);
        }
        int deletedCount = characterPovSummaryRepository.deleteByChapter(chapter);
        chapter.markPovSummariesAsUncached();
        bookAnalysisStatusService.resetToNone(bookId);
        return deletedCount;
    }

    /**
     * ?諭????源?紐꾨퓠 ?怨뚭퍙??筌뤴뫀諭??온???類ｋ궖???????몃빍??
     */
    @Transactional
    public int deleteRelationships(Long bookId, Integer chapterIdx, Integer eventIdx) {
        Chapter chapter = chapterRepository.findByBookIdAndIdx(bookId, chapterIdx)
                .orElseThrow(() -> new GeneralException(ErrorStatus.CHAPTER_NOT_FOUND));

        Event event = eventRepository.findByChapterAndIdx(chapter, eventIdx)
                .orElseThrow(() -> new GeneralException(ErrorStatus.EVENT_NOT_FOUND));

        boolean edgesExist = eventRelationshipEdgeRepository.existsByEvent(event);
        boolean statsExist = statRepository.existsByEvent(event);
        if (!edgesExist && !statsExist) {
            throw new GeneralException(ErrorStatus.NO_RELATIONSHIPS_TO_DELETE, "No relationships exist for the event.");
        }

        int deletedCount = 0;
        if (edgesExist) {
            deletedCount += eventRelationshipEdgeRepository.deleteByEvent(event);
        }
        if (statsExist) {
            deletedCount += statRepository.deleteByEvent(event);
        }

        bookAnalysisStatusService.resetToNone(bookId);
        return deletedCount;
    }

    /*
     * =====================================================================================
     * 4. ??? ????筌롫뗄苑??獄??????
     * =====================================================================================
     */

    /**
     * 筌?굞肉???곷립 筌뤴뫀諭?筌?벤苑???遺용튋???袁⑥┷??뤿??遺? ?類ㅼ뵥??랁? 筌?굞???袁⑷퍥 ?遺용튋 ?怨밴묶????낅쑓??꾨뱜??몃빍??
     */
    private void checkAndUpdateBookSummaryStatus(Book book) {
        // ??? ?袁⑥┷??筌?굞? 野꺜??釉??袁⑹뒄 ??곸벉
        if (book.isSummary()) {
            return;
        }
        List<Chapter> chapters = chapterRepository.findByBookId(book.getId());
        boolean allChaptersSummarized = chapters.stream().allMatch(Chapter::isPovSummariesCached);
        if (allChaptersSummarized) {
            book.completeSummary();
        }
    }

    private void markAnalysisRejectedIfNeeded(Book book, GeneralException exception) {
        if (book == null || book.isAnalysisReady()) {
            return;
        }

        HttpStatus httpStatus = exception.getErrorReasonHttpStatus().getHttpStatus();
        if (httpStatus != null && httpStatus.is4xxClientError()) {
            bookAnalysisStatusService.markRejectedIfPending(book.getId());
        }
    }

    private Integer parseEventIdx(String eventId, Integer chapterIdx) {
        Matcher matcher = Pattern.compile("^ch(\\d+)-e(\\d+)$").matcher(eventId);
        if (matcher.matches()) {
            Integer eventChapterIdx = Integer.parseInt(matcher.group(1));
            if (!chapterIdx.equals(eventChapterIdx)) {
                throw new GeneralException(ErrorStatus._BAD_REQUEST, "eventId??chapterIndex?? ??낆쨮??筌?벤苑ｅ첎? ??깊뒄??? ??녿뮸??덈뼄.");
            }
            return Integer.parseInt(matcher.group(2));
        }

        try {
            return Integer.parseInt(eventId);
        } catch (NumberFormatException e) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "eventId ?類ㅻ뻼????而?몴?? ??녿뮸??덈뼄: " + eventId);
        }
    }

    private String normalizeEventId(String eventId, Integer chapterIdx, Integer eventIdx) {
        if (eventId.startsWith("ch")) {
            return eventId;
        }
        return "ch" + chapterIdx + "-e" + eventIdx;
    }

    private LocatorDTO resolveEventLocator(Chapter chapter, Integer txtOffset) {
        return locatorSupport.toLocator(chapter, txtOffset);
    }

    private String resolveCharacterDescription(CharacterDTO dto) {
        if (dto.getDescriptions() != null) {
            String description = normalizeOptionalText(dto.getDescriptions().get("ko"));
            if (description != null) {
                return description;
            }
        }
        return normalizeOptionalText(dto.getLegacyDescriptionKo());
    }

    private String joinNames(List<String> names, String fallbackName) {
        if (names == null || names.isEmpty()) {
            return fallbackName;
        }

        String joined = names.stream()
                .map(this::normalizeOptionalText)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .collect(Collectors.joining(","));
        return joined.isBlank() ? fallbackName : joined;
    }

    private Long parseCharacterId(String rawValue, String fieldName) {
        String normalized = requireText(rawValue, fieldName);
        if (normalized.matches("\\d+")) {
            return Long.parseLong(normalized);
        }

        Matcher matcher = Pattern.compile("^c0*(\\d+)$", Pattern.CASE_INSENSITIVE)
                .matcher(normalized);
        if (matcher.matches()) {
            return Long.parseLong(matcher.group(1));
        }

        throw new GeneralException(ErrorStatus._BAD_REQUEST, fieldName + " ?類ㅻ뻼????而?몴?? ??녿뮸??덈뼄: " + rawValue);
    }

    private String loadNormalizedChapterText(Book book, int chapterIdx) {
        String artifactRoot = book.getNormalizedArtifactPath();
        if (artifactRoot == null || artifactRoot.isBlank()) {
            throw new GeneralException(ErrorStatus._INTERNAL_SERVER_ERROR, "Normalized chapter text is not available for validation.");
        }

        try {
            return normalizeChapterText(normalizedArtifactStorageService.loadNormalizedChapterText(artifactRoot, chapterIdx));
        } catch (RuntimeException e) {
            log.error("Failed to load normalized chapter text. bookId={}, chapterIdx={}", book.getId(), chapterIdx, e);
            throw new GeneralException(ErrorStatus._INTERNAL_SERVER_ERROR, "Failed to load normalized chapter text for validation.");
        }
    }

    private void validateEventText(String chapterText, int startTxtOffset, int endTxtOffset, String eventText, String eventId) {
        if (chapterText == null) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "筌?벤苑??癒???餓Β??쑬由븝쭪? ??녿툡 eventText??野꺜筌앹빜釉?????곷뮸??덈뼄.");
        }

        int totalCodePoints = chapterText.codePointCount(0, chapterText.length());
        if (startTxtOffset < 0 || endTxtOffset > totalCodePoints) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "??源??txtOffset 甕곕뗄?욃첎? 筌?벤苑?甕곕뗄?욅몴?甕곗щ선?????덈뼄: " + eventId);
        }

        String expected = substringByCodePoints(chapterText, startTxtOffset, endTxtOffset);
        String actual = normalizeChapterText(eventText);
        if (!expected.equals(actual)) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "eventText?? chapterTxt[start:end]揶쎛 ??깊뒄??? ??녿뮸??덈뼄: " + eventId);
        }
    }

    private String normalizeChapterText(String rawText) {
        if (rawText == null) {
            return null;
        }
        return rawText.replace("\r\n", "\n").replace('\r', '\n');
    }

    private String substringByCodePoints(String text, int start, int end) {
        int startIndex = text.offsetByCodePoints(0, start);
        int endIndex = text.offsetByCodePoints(0, end);
        return text.substring(startIndex, endIndex);
    }

    private String requireText(String value, String fieldName) {
        String normalized = normalizeOptionalText(value);
        if (normalized == null) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, fieldName + "???袁⑸땾??낅빍??");
        }
        return normalized;
    }

    private String requireRawText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, fieldName + "???袁⑸땾??낅빍??");
        }
        return value;
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
