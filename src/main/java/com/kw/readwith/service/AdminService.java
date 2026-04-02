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
import com.kw.readwith.service.normalization.NormalizationVersionService;
import com.kw.readwith.util.LocatorSupport;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private final EventCharacterStatRepository statRepository;
    private final ObjectMapper objectMapper;
    private final CharacterImageService characterImageService;
    private final LocatorSupport locatorSupport;
    private final V2TransitionGuard transitionGuard;
    private final NormalizationVersionService normalizationVersionService;
    
    @PersistenceContext
    private EntityManager entityManager;

    /*
     * =====================================================================================
     * 1. 데이터 업로드
     * =====================================================================================
     */

    /**
     * 등장인물 정보가 담긴 JSON 파일을 업로드합니다.
     */
    @Transactional
    public int uploadCharacters(Long bookId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "업로드 파일이 비어있습니다.");
        }

        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));

        try {
            CharacterListDTO characterListDTO = objectMapper.readValue(file.getInputStream(), CharacterListDTO.class);

            if (characterListDTO == null || characterListDTO.getItems() == null) {
                throw new GeneralException(ErrorStatus._BAD_REQUEST, "캐릭터 데이터가 올바르지 않습니다.");
            }

            if (characterListDTO.getItems().isEmpty()) {
                log.warn("업로드할 캐릭터가 없습니다.");
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
                    log.info("캐릭터 ID '{}' 이미 존재하여 스킵", characterId);
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
                log.info("저장할 새로운 캐릭터가 없습니다.");
                return 0;
            }

            //  DB 저장 with 예외 처리
            List<Character> savedCharacters;
            try {
                savedCharacters = characterRepository.saveAll(newCharacters);
                log.info("캐릭터 {}명 DB 저장 완료", savedCharacters.size());
            } catch (Exception e) {
                log.error("캐릭터 DB 저장 실패", e);
                throw new GeneralException(ErrorStatus._INTERNAL_SERVER_ERROR, "캐릭터 저장 중 데이터베이스 오류가 발생했습니다.");
            }
                
            // 캐릭터 ID만 추출하여 비동기 메서드에 전달
            List<Long> characterIds = savedCharacters.stream()
                    .map(Character::getId)
                    .collect(Collectors.toList());
            
            // 이미지 생성 (실패해도 계속 진행)
            try {
                log.info("캐릭터 이미지 생성 백그라운드 작업 시작");
                characterImageService.generateImagesAsync(characterIds);
            } catch (Exception e) {
                log.error("캐릭터 이미지 생성 시작 실패 (백그라운드 작업, 계속 진행)", e);
            }
            
            return savedCharacters.size();

        } catch (IOException e) {
            log.error("캐릭터 JSON 파일 파싱 실패", e);
            throw new GeneralException(ErrorStatus.JSON_PARSING_ERROR, "JSON 파일 형식이 올바르지 않습니다.");
        } catch (GeneralException e) {
            throw e;
        } catch (Exception e) {
            log.error("캐릭터 업로드 중 예상치 못한 오류", e);
            throw new GeneralException(ErrorStatus._INTERNAL_SERVER_ERROR, "캐릭터 업로드 처리 중 오류가 발생했습니다.");
        }
    }

    /**
     * 여러 챕터의 이벤트 JSON 파일들을 한번에 업로드합니다.
     */
    @Transactional
    public int uploadEvents(Long bookId, List<MultipartFile> eventFiles) {
        if (eventFiles == null || eventFiles.isEmpty()) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "업로드할 이벤트 파일이 없습니다.");
        }

        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));
        transitionGuard.ensureLocatorWritesEnabled("admin event upload");

        List<Event> allNewEvents = new ArrayList<>();

        for (MultipartFile eventFile : eventFiles) {
            if (eventFile == null || eventFile.isEmpty()) {
                log.warn("빈 파일이 포함되어 스킵합니다.");
                continue;
            }

            try {
                EventUploadDTO uploadDTO = objectMapper.readValue(eventFile.getInputStream(), EventUploadDTO.class);
                if (uploadDTO == null || uploadDTO.getChapterIndex() == null) {
                    throw new GeneralException(ErrorStatus._BAD_REQUEST, "event payload chapterIndex는 필수입니다.");
                }

                Integer chapterIdx = uploadDTO.getChapterIndex();
                Chapter chapter = chapterRepository.findByBookIdAndIdx(bookId, chapterIdx)
                        .orElseThrow(() -> new GeneralException(ErrorStatus.CHAPTER_NOT_FOUND, "챕터 인덱스 " + chapterIdx + "를 찾을 수 없습니다."));
                transitionGuard.ensureLocatorMetadataReady(book, chapter, "admin event upload");

                if (eventRepository.existsByChapter(chapter)) {
                    throw new GeneralException(ErrorStatus.EVENT_DATA_ALREADY_EXISTS, "챕터 " + chapterIdx + "의 이벤트는 이미 존재합니다.");
                }

                List<EventDTO> eventDTOs = uploadDTO.getItems();
                if (eventDTOs == null || eventDTOs.isEmpty()) {
                    log.warn("파일 '{}'에 이벤트 데이터가 없습니다.", eventFile.getOriginalFilename());
                    continue;
                }

                Set<Integer> seenEventIndexes = new LinkedHashSet<>();
                List<Event> newEventsFromFile = new ArrayList<>();
                for (EventDTO dto : eventDTOs) {
                    String eventId = requireText(dto.getEventId(), "event.eventId");
                    if (dto.getChapterIndex() != null && !chapterIdx.equals(dto.getChapterIndex())) {
                        throw new GeneralException(ErrorStatus._BAD_REQUEST, "payload chapterIndex가 root chapterIndex와 일치하지 않습니다.");
                    }

                    Integer startTxtOffset = dto.getStartTxtOffset() != null ? dto.getStartTxtOffset() : dto.getStart();
                    Integer endTxtOffset = dto.getEndTxtOffset() != null ? dto.getEndTxtOffset() : dto.getEnd();
                    if (startTxtOffset == null || endTxtOffset == null) {
                        throw new GeneralException(ErrorStatus._BAD_REQUEST, "이벤트 위치 정보가 없습니다: " + eventId);
                    }
                    if (startTxtOffset >= endTxtOffset) {
                        throw new GeneralException(ErrorStatus._BAD_REQUEST, "이벤트 txtOffset 범위가 올바르지 않습니다: " + eventId);
                    }

                    String rawText = requireRawText(dto.getEventText() != null ? dto.getEventText() : dto.getText(), "event.eventText");
                    validateEventText(chapter, startTxtOffset, endTxtOffset, rawText, eventId);

                    Integer eventIdx = parseEventIdx(eventId, chapterIdx);
                    if (!seenEventIndexes.add(eventIdx)) {
                        throw new GeneralException(ErrorStatus._BAD_REQUEST, "중복 eventId가 포함되어 있습니다: " + eventId);
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
                            .rawText(rawText != null ? rawText : "")
                            .build();
                    newEventsFromFile.add(event);
                }

                allNewEvents.addAll(newEventsFromFile);
                log.info("파일 '{}' 처리 완료: {}개 이벤트", eventFile.getOriginalFilename(), newEventsFromFile.size());

            } catch (IOException e) {
                log.error("이벤트 JSON 파일 파싱 실패: {}", eventFile.getOriginalFilename(), e);
                throw new GeneralException(ErrorStatus.JSON_PARSING_ERROR, "JSON 파일 파싱 실패: " + eventFile.getOriginalFilename());
            }
        }

        if (allNewEvents.isEmpty()) {
            log.warn("저장할 이벤트가 없습니다.");
            return 0;
        }
        
        int batchSize = 500;
        int totalSaved = 0;
        
        try {
            for (int i = 0; i < allNewEvents.size(); i += batchSize) {
                int end = Math.min(i + batchSize, allNewEvents.size());
                List<Event> batch = allNewEvents.subList(i, end);
                
                eventRepository.saveAll(batch);
                entityManager.flush();   // 즉시 DB 반영
                entityManager.clear();   // 1차 캐시 비우기 (메모리 절약)
                
                totalSaved += batch.size();
                log.info("이벤트 배치 진행: {}/{}", totalSaved, allNewEvents.size());
            }
            log.info("이벤트 {}개 DB 저장 완료", totalSaved);
        } catch (Exception e) {
            log.error("이벤트 DB 저장 실패", e);
            throw new GeneralException(ErrorStatus._INTERNAL_SERVER_ERROR, "이벤트 저장 중 데이터베이스 오류가 발생했습니다.");
        }
        
        return totalSaved;
    }

    /**
     * 여러 챕터의 POV 요약 JSON 파일들을 한번에 업로드합니다.
     */
    @Transactional
    public int uploadChapterSummaries(Long bookId, List<MultipartFile> summaryFiles) {
        if (summaryFiles == null || summaryFiles.isEmpty()) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "업로드할 요약 파일이 없습니다.");
        }

        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));

        List<CharacterPovSummary> allNewSummaries = new ArrayList<>();

        for (MultipartFile summaryFile : summaryFiles) {
            if (summaryFile == null || summaryFile.isEmpty()) {
                log.warn("빈 요약 파일이 포함되어 스킵합니다.");
                continue;
            }

            SummaryUploadDTO uploadDTO;
            try {
                uploadDTO = objectMapper.readValue(summaryFile.getInputStream(), SummaryUploadDTO.class);
            } catch (IOException e) {
                throw new GeneralException(ErrorStatus.JSON_PARSING_ERROR);
            }

            if (uploadDTO == null || uploadDTO.getChapterIndex() == null) {
                throw new GeneralException(ErrorStatus._BAD_REQUEST, "summary payload chapterIndex는 필수입니다.");
            }
            if (uploadDTO.getLanguage() != null && !"ko".equalsIgnoreCase(uploadDTO.getLanguage())) {
                log.info("요약 파일 '{}'은(는) ko가 아니므로 스킵합니다. language={}",
                        summaryFile.getOriginalFilename(), uploadDTO.getLanguage());
                continue;
            }

            Integer chapterIdx = uploadDTO.getChapterIndex();
            Chapter chapter = chapterRepository.findByBookIdAndIdx(bookId, chapterIdx)
                    .orElseThrow(() -> new GeneralException(ErrorStatus.CHAPTER_NOT_FOUND, "챕터 인덱스 " + chapterIdx + "를 찾을 수 없습니다."));

            if (characterPovSummaryRepository.existsByChapter(chapter)) {
                throw new GeneralException(ErrorStatus.CHAPTER_ALREADY_SUMMARIZED, "챕터 " + chapterIdx + "의 요약본은 이미 존재합니다.");
            }

            if (uploadDTO.getItems() != null) {
                List<CharacterPovSummary> newSummariesFromFile = uploadDTO.getItems().stream()
                        .map(item -> {
                            Long characterId = parseCharacterId(item.getCharacterId(), "summary.characterId");
                            Character character = characterRepository.findByBookAndCharacterId(chapter.getBook(), characterId)
                                    .orElseThrow(() -> new GeneralException(ErrorStatus.CHARACTER_NOT_FOUND,
                                            "Character not found with jsonId: " + characterId));

                            return CharacterPovSummary.builder()
                                    .book(chapter.getBook())
                                    .chapter(chapter)
                                    .character(character)
                                    .summaryText(requireText(item.getSummary(), "summary.summary"))
                                    .build();
                        })
                        .collect(Collectors.toList());
                allNewSummaries.addAll(newSummariesFromFile);
            }
            chapter.markAsSummarized();
        }

        if (!allNewSummaries.isEmpty()) {
            try {
                characterPovSummaryRepository.saveAll(allNewSummaries);
                log.info("챕터 요약 {}개 DB 저장 완료", allNewSummaries.size());
            } catch (Exception e) {
                log.error("챕터 요약 DB 저장 실패", e);
                throw new GeneralException(ErrorStatus._INTERNAL_SERVER_ERROR, "챕터 요약 저장 중 데이터베이스 오류가 발생했습니다.");
            }
        }

        try {
            checkAndUpdateBookSummaryStatus(book);
        } catch (Exception e) {
            log.error("책 요약 상태 업데이트 중 오류 발생", e);
        }
        return allNewSummaries.size();
    }

    /**
     * 여러 관계 JSON 파일을 업로드하고 DB에 저장
     * 파일 이름 규칙: chapter<번호>_..._event_<번호>.json
     *
     * @param bookId 관계 정보를 추가할 책의 ID
     * @param files  업로드할 관계 정보 JSON 파일 목록
     */
    @Transactional
    public int uploadRelationships(Long bookId, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "업로드할 관계 파일이 없습니다.");
        }

        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));

        int totalProcessedCount = 0;

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                log.warn("빈 관계 파일이 포함되어 스킵합니다.");
                continue;
            }

            try {
                RelationshipUploadDTO dto = objectMapper.readValue(file.getInputStream(), RelationshipUploadDTO.class);
                if (dto == null || dto.getChapterIndex() == null) {
                    throw new GeneralException(ErrorStatus._BAD_REQUEST, "relationship payload chapterIndex는 필수입니다.");
                }
                String eventId = requireText(dto.getEventId(), "relationship.eventId");
                int chapterIdx = dto.getChapterIndex();
                int eventIdx = parseEventIdx(eventId, chapterIdx);

                Event event = eventRepository.findByBookIdAndChapterIdxAndEventIdx(bookId, chapterIdx, eventIdx)
                        .orElseThrow(() -> new GeneralException(ErrorStatus.EVENT_NOT_FOUND,
                                String.format("이벤트를 찾을 수 없습니다 (책 ID: %d, 챕터: %d, 이벤트: %d)", bookId, chapterIdx, eventIdx)));

                totalProcessedCount += processNodeWeights(event, book, dto.getNodeWeights());
                totalProcessedCount += processRelations(event, book, dto.getItems());

            } catch (IOException e) {
                throw new GeneralException(ErrorStatus.JSON_PARSING_ERROR, "관계 정보 JSON 파일 파싱에 실패했습니다: " + file.getOriginalFilename());
            }
        }
        return totalProcessedCount;
    }

    /**
     * JSON의 'node_weights_accum' 부분을 처리하는 헬퍼 메서드
     * 각 캐릭터의 가중치(weight)를 저장하기 전, 중복 데이터가 있는지 확인
     *
     * @param event          현재 처리 중인 이벤트 엔터티
     * @param book           현재 처리 중인 책 엔터티
     * @param nodeWeightsMap JSON에서 파싱된 캐릭터 ID와 가중치 정보가 담긴 맵
     */
    private int processNodeWeights(Event event, Book book, Map<String, NodeWeightDTO> nodeWeightsMap) {
        if (nodeWeightsMap == null || nodeWeightsMap.isEmpty()) {
            return 0;
        }

        List<EventCharacterStat> newStats = new ArrayList<>();
        for (Map.Entry<String, NodeWeightDTO> entry : nodeWeightsMap.entrySet()) {
            Long characterBookId = parseCharacterId(entry.getKey(), "relationship.nodeWeights.characterId");
            NodeWeightDTO weightDTO = entry.getValue();
            if (weightDTO == null) {
                throw new GeneralException(ErrorStatus._BAD_REQUEST, "nodeWeights payload가 비어 있습니다: " + characterBookId);
            }

            Character character = characterRepository.findByBookAndCharacterId(book, characterBookId)
                    .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_CHARACTER_NOT_FOUND,
                            "해당 책에 등록된 캐릭터를 찾을 수 없습니다: " + characterBookId));

            if (statRepository.findByEventAndCharacter(event, character).isPresent()) {
                throw new GeneralException(ErrorStatus.NODE_WEIGHT_ALREADY_EXISTS,
                        String.format("해당 이벤트에 대한 '%s' 캐릭터의 가중치 데이터가 이미 존재합니다.", character.getName()));
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
            log.warn("저장할 노드 가중치가 없습니다.");
            return 0;
        }

        try {
            statRepository.saveAll(newStats);
            log.info("노드 가중치 {}개 DB 저장 완료", newStats.size());
        } catch (Exception e) {
            log.error("노드 가중치 DB 저장 실패", e);
            throw new GeneralException(ErrorStatus._INTERNAL_SERVER_ERROR, "노드 가중치 저장 중 데이터베이스 오류가 발생했습니다.");
        }
        return newStats.size();
    }

    /**
     * JSON의 'relations' 부분을 처리하는 헬퍼 메서드
     * 인물 관계를 저장하기 전, 중복 데이터가 있는지 확인
     *
     * @param event        현재 처리 중인 이벤트 엔터티
     * @param book         현재 처리 중인 책 엔터티
     * @param relationDTOs JSON에서 파싱된 관계 정보 DTO 목록
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
                throw new GeneralException(ErrorStatus._BAD_REQUEST, "relationship positivity/evidenceCount는 필수입니다.");
            }

            Character fromChar = characterRepository.findByBookAndCharacterId(book, fromCharacterId)
                    .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_CHARACTER_NOT_FOUND,
                            "해당 책에 등록된 캐릭터를 찾을 수 없습니다: " + fromCharacterId));
            Character toChar = characterRepository.findByBookAndCharacterId(book, toCharacterId)
                    .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_CHARACTER_NOT_FOUND,
                            "해당 책에 등록된 캐릭터를 찾을 수 없습니다: " + toCharacterId));

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
                        .interactionCount(dto.getEvidenceCount())
                        .relationTags(objectMapper.writeValueAsString(dto.getLabels() != null ? dto.getLabels() : List.of()))
                        .build();
                newEdges.add(edge);
            } catch (JsonProcessingException e) {
                throw new GeneralException(ErrorStatus.JSON_PARSING_ERROR,
                        String.format("캐릭터 %d와(과) %d의 관계 태그 처리 중 오류가 발생했습니다.", fromCharacterId, toCharacterId));
            }
        }

        if (newEdges.isEmpty()) {
            log.warn("저장할 관계 엣지가 없습니다.");
            return 0;
        }

        try {
            eventRelationshipEdgeRepository.saveAll(newEdges);
            log.info("관계 엣지 {}개 DB 저장 완료", newEdges.size());
        } catch (Exception e) {
            log.error("관계 엣지 DB 저장 실패", e);
            throw new GeneralException(ErrorStatus._INTERNAL_SERVER_ERROR, "관계 데이터 저장 중 데이터베이스 오류가 발생했습니다.");
        }
        return newEdges.size();
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
                        .isFavorite(false) // 관리자 페이지에서는 즐겨찾기 정보가 불필요
                        .summary(book.isSummary())
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

    /**
     * 특정 캐릭터의 프로필 이미지를 재생성합니다.
     * 이미지 생성에 실패했거나 품질이 좋지 않은 경우 사용합니다.
     * 
     * @param characterId 이미지를 재생성할 캐릭터 ID
     */
    @Transactional
    public void regenerateCharacterImage(Long characterId) {
        // 캐릭터 존재 여부 확인
        Character character = characterRepository.findById(characterId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.CHARACTER_NOT_FOUND, 
                        "캐릭터를 찾을 수 없습니다: " + characterId));
        
        log.info("캐릭터 '{}' (ID: {}) 이미지 재생성 시작", character.getName(), characterId);
        
        // 기존 이미지 상태 로깅
        if (character.getProfileImage() != null && !character.getProfileImage().isEmpty()) {
            log.info("기존 이미지 URL: {}, 상태: {}", 
                    character.getProfileImage(), character.getImageGenerationStatus());
        } else {
            log.info("기존 이미지 없음, 상태: {}", character.getImageGenerationStatus());
        }
        
        // 이미지 재생성 (비동기가 아닌 동기 방식으로 호출)
        try {
            characterImageService.generateAndSaveImage(characterId);
            log.info("캐릭터 '{}' (ID: {}) 이미지 재생성 완료", character.getName(), characterId);
        } catch (Exception e) {
            log.error("캐릭터 '{}' (ID: {}) 이미지 재생성 실패", character.getName(), characterId, e);
            throw new GeneralException(ErrorStatus._INTERNAL_SERVER_ERROR, 
                    "이미지 재생성 중 오류가 발생했습니다: " + e.getMessage());
        }
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
    public int deleteCharacters(Long bookId) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));

        if (!characterRepository.existsByBook(book)) {
            throw new GeneralException(ErrorStatus.NO_CHARACTERS_TO_DELETE);
        }
        return characterRepository.deleteByBook(book);
    }

    /**
     * 특정 챕터에 속한 모든 이벤트를 삭제합니다.
     */
    @Transactional
    public int deleteEvents(Long bookId, Integer chapterIdx) {
        Chapter chapter = chapterRepository.findByBookIdAndIdx(bookId, chapterIdx)
                .orElseThrow(() -> new GeneralException(ErrorStatus.CHAPTER_NOT_FOUND));

        if (!eventRepository.existsByChapter(chapter)) {
            throw new GeneralException(ErrorStatus.NO_EVENTS_TO_DELETE);
        }
        return eventRepository.deleteByChapter(chapter);
    }

    /**
     * 특정 챕터의 모든 POV 요약본을 삭제합니다.
     */
    @Transactional
    public int deleteChapterSummary(Long bookId, Integer chapterIdx) {
        Chapter chapter = chapterRepository.findByBookIdAndIdx(bookId, chapterIdx)
                .orElseThrow(() -> new GeneralException(ErrorStatus.CHAPTER_NOT_FOUND));

        if (!characterPovSummaryRepository.existsByChapter(chapter)) {
            throw new GeneralException(ErrorStatus.NO_SUMMARY_TO_DELETE);
        }
        int deletedCount = characterPovSummaryRepository.deleteByChapter(chapter);
        // 챕터의 요약 상태를 '미완료'로 되돌림
        chapter.markPovSummariesAsUncached();
        return deletedCount;
    }

    /**
     * 특정 이벤트에 연결된 모든 관계 정보를 삭제합니다.
     */
    @Transactional
    public int deleteRelationships(Long bookId, Integer chapterIdx, Integer eventIdx) {
        Chapter chapter = chapterRepository.findByBookIdAndIdx(bookId, chapterIdx)
                .orElseThrow(() -> new GeneralException(ErrorStatus.CHAPTER_NOT_FOUND));

        Event event = eventRepository.findByChapterAndIdx(chapter, eventIdx)
                .orElseThrow(() -> new GeneralException(ErrorStatus.EVENT_NOT_FOUND));

        // 삭제할 데이터가 있는지 확인
        boolean edgesExist = eventRelationshipEdgeRepository.existsByEvent(event);
        boolean statsExist = statRepository.existsByEvent(event);

        if (!edgesExist && !statsExist) {
            throw new GeneralException(ErrorStatus.NO_RELATIONSHIPS_TO_DELETE, "해당 이벤트에 삭제할 관계 정보가 없습니다.");
        }

        int deletedCount = 0;

        // EventRelationshipEdge 데이터 삭제
        if (edgesExist) {
            deletedCount += eventRelationshipEdgeRepository.deleteByEvent(event);
        }

        // EventCharacterStat 데이터 삭제
        if (statsExist) {
            deletedCount += statRepository.deleteByEvent(event);
        }

        return deletedCount;
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

    private Integer parseEventIdx(String eventId, Integer chapterIdx) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^ch(\\d+)-e(\\d+)$").matcher(eventId);
        if (matcher.matches()) {
            Integer eventChapterIdx = Integer.parseInt(matcher.group(1));
            if (!chapterIdx.equals(eventChapterIdx)) {
                throw new GeneralException(ErrorStatus._BAD_REQUEST, "eventId의 chapterIndex와 업로드 챕터가 일치하지 않습니다.");
            }
            return Integer.parseInt(matcher.group(2));
        }

        try {
            return Integer.parseInt(eventId);
        } catch (NumberFormatException e) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "eventId 형식이 올바르지 않습니다: " + eventId);
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

        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^c0*(\\d+)$", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(normalized);
        if (matcher.matches()) {
            return Long.parseLong(matcher.group(1));
        }

        throw new GeneralException(ErrorStatus._BAD_REQUEST, fieldName + " 형식이 올바르지 않습니다: " + rawValue);
    }

    private void validateEventText(Chapter chapter, int startTxtOffset, int endTxtOffset, String eventText, String eventId) {
        String chapterText = normalizeChapterText(chapter.getRawText());
        if (chapterText == null) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "챕터 원문이 준비되지 않아 eventText를 검증할 수 없습니다.");
        }

        int totalCodePoints = chapterText.codePointCount(0, chapterText.length());
        if (startTxtOffset < 0 || endTxtOffset > totalCodePoints) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "이벤트 txtOffset 범위가 챕터 범위를 벗어났습니다: " + eventId);
        }

        String expected = substringByCodePoints(chapterText, startTxtOffset, endTxtOffset);
        String actual = normalizeChapterText(eventText);
        if (!expected.equals(actual)) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "eventText와 chapterTxt[start:end]가 일치하지 않습니다: " + eventId);
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
            throw new GeneralException(ErrorStatus._BAD_REQUEST, fieldName + "는 필수입니다.");
        }
        return normalized;
    }

    private String requireRawText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, fieldName + "는 필수입니다.");
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

