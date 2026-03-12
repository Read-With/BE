package com.kw.readwith.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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
import com.kw.readwith.util.LocatorSupport;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
    private final EventCharacterStatRepository statRepository;
    private final ObjectMapper objectMapper;
    private final CharacterImageService characterImageService;
    private final LocatorSupport locatorSupport;
    private final V2TransitionGuard transitionGuard;
    
    @PersistenceContext
    private EntityManager entityManager;

    // 파일명에서 챕터와 이벤트 인덱스를 추출하기 위한 정규표현식 패턴
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
    public int uploadCharacters(Long bookId, MultipartFile file) {
        // 파일 유효성 검증
        if (file == null || file.isEmpty()) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "업로드 파일이 비어있습니다.");
        }
        
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));

        try {
            CharacterListDTO characterListDTO = objectMapper.readValue(file.getInputStream(), CharacterListDTO.class);
            
            // DTO 유효성 검증
            if (characterListDTO == null || characterListDTO.getCharacters() == null) {
                throw new GeneralException(ErrorStatus._BAD_REQUEST, "캐릭터 데이터가 올바르지 않습니다.");
            }
            
            if (characterListDTO.getCharacters().isEmpty()) {
                log.warn("업로드할 캐릭터가 없습니다.");
                return 0;
            }

            List<Character> newCharacters = new ArrayList<>();
            for (CharacterDTO dto : characterListDTO.getCharacters()) {
                // 필수 필드 검증
                if (dto.getCommonName() == null || dto.getCommonName().trim().isEmpty()) {
                    log.warn("캐릭터 이름이 없어 스킵합니다: {}", dto);
                    continue;
                }
                if (dto.getId() == null) {
                    log.warn("캐릭터 ID가 없어 스킵합니다: {}", dto.getCommonName());
                    continue;
                }
                
                // 중복 저장을 방지하기 위해, 해당 이름의 캐릭터가 없는 경우에만 추가
                Optional<Character> existingCharacter = characterRepository.findByBookAndName(book, dto.getCommonName());

                if (existingCharacter.isEmpty()) {
                    // names 안전 처리
                    String namesStr = "";
                    if (dto.getNames() != null && !dto.getNames().isEmpty()) {
                        namesStr = String.join(",", dto.getNames());
                    }
                    
                    Character character = Character.builder()
                            .book(book)
                            .characterId(dto.getId())
                            .name(dto.getCommonName().trim())
                            .names(namesStr)
                            .isMainCharacter(dto.isMainCharacter())
                            .personalityText(dto.getDescription_ko())
                            .profileText(dto.getPortraitPrompt())
                            .imageGenerationStatus(ImageGenerationStatus.PENDING)
                            .build();
                    newCharacters.add(character);
                } else {
                    log.info("캐릭터 '{}' 이미 존재하여 스킵", dto.getCommonName());
                }
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
            // 이미 처리된 예외는 그대로 전파
            throw e;
        } catch (Exception e) {
            log.error("캐릭터 업로드 중 예상치 못한 오류", e);
            throw new GeneralException(ErrorStatus._INTERNAL_SERVER_ERROR, "캐릭터 업로드 처리 중 오류가 발생했습니다.");
        }
    }

    /**
     * 여러 챕터의 이벤트 JSON 파일들을 한번에 업로드합니다.
     * 파일 이름(chapter<번호>_events.json)에서 챕터 번호를 자동으로 인식합니다.
     * 
     * 배치 처리: 대량 데이터를 500개씩 나눠서 저장 (메모리 최적화)
     */
    @Transactional
    public int uploadEvents(Long bookId, List<MultipartFile> eventFiles) {
        // 파일 리스트 유효성 검증
        if (eventFiles == null || eventFiles.isEmpty()) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "업로드할 이벤트 파일이 없습니다.");
        }
        
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));
        transitionGuard.ensureLocatorWritesEnabled("admin event upload");

        List<Event> allNewEvents = new ArrayList<>();
        // 파일명에서 챕터 번호를 추출하기 위한 정규표현식
        Pattern pattern = Pattern.compile("chapter(\\d+)_events\\.json");

        for (MultipartFile eventFile : eventFiles) {
            // 파일 유효성 검증
            if (eventFile == null || eventFile.isEmpty()) {
                log.warn("빈 파일이 포함되어 스킵합니다.");
                continue;
            }
            
            String filename = eventFile.getOriginalFilename();
            if (filename == null || filename.trim().isEmpty()) {
                log.warn("파일명이 없는 파일을 스킵합니다.");
                continue;
            }
            
            Matcher matcher = pattern.matcher(filename);

            if (!matcher.matches()) {
                throw new GeneralException(ErrorStatus.INVALID_FILE_NAME_FORMAT, "파일명 형식이 올바르지 않습니다: " + filename);
            }

            // 타입 변환 예외 처리
            Integer chapterIdx;
            try {
                chapterIdx = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                log.error("챕터 번호 파싱 실패: {}", filename, e);
                throw new GeneralException(ErrorStatus.INVALID_FILE_NAME_FORMAT, "챕터 번호가 올바르지 않습니다: " + filename);
            }
            
            Chapter chapter = chapterRepository.findByBookIdAndIdx(bookId, chapterIdx)
                    .orElseGet(() -> {
                        Chapter newChapter = Chapter.builder()
                                .book(book)
                                .idx(chapterIdx)
                                .build();
                        return chapterRepository.save(newChapter);
                    });
            transitionGuard.ensureLocatorMetadataReady(book, chapter, "admin event upload");

            // 이미 데이터가 있는 챕터는 업로드 불가
            if (eventRepository.existsByChapter(chapter)) {
                throw new GeneralException(ErrorStatus.EVENT_DATA_ALREADY_EXISTS, "챕터 " + chapterIdx + "의 이벤트는 이미 존재합니다.");
            }

            try {
                List<EventDTO> eventDTOs = objectMapper.readValue(eventFile.getInputStream(), new TypeReference<List<EventDTO>>() {});
                
                // DTO 유효성 검증
                if (eventDTOs == null || eventDTOs.isEmpty()) {
                    log.warn("파일 '{}'에 이벤트 데이터가 없습니다.", filename);
                    continue;
                }
                
                List<Event> newEventsFromFile = new ArrayList<>();
                for (EventDTO dto : eventDTOs) {
                    // 필수 필드 검증
                    if (dto.getEventId() == null) {
                        log.warn("이벤트 ID가 없어 스킵합니다 (파일: {})", filename);
                        continue;
                    }
                    if (dto.getChapterIndex() != null && !chapterIdx.equals(dto.getChapterIndex())) {
                        throw new GeneralException(ErrorStatus._BAD_REQUEST, "payload chapterIndex와 파일명 chapterIndex가 일치하지 않습니다.");
                    }

                    Integer startTxtOffset = dto.getStartTxtOffset() != null ? dto.getStartTxtOffset() : dto.getStart();
                    Integer endTxtOffset = dto.getEndTxtOffset() != null ? dto.getEndTxtOffset() : dto.getEnd();
                    if (startTxtOffset == null || endTxtOffset == null) {
                        log.warn("이벤트 위치 정보가 없어 스킵합니다 (파일: {}, eventId: {})", filename, dto.getEventId());
                        continue;
                    }
                    if (startTxtOffset >= endTxtOffset) {
                        throw new GeneralException(ErrorStatus._BAD_REQUEST, "이벤트 txtOffset 범위가 올바르지 않습니다: " + dto.getEventId());
                    }

                    Integer eventIdx = parseEventIdx(dto.getEventId(), chapterIdx);
                    String normalizedEventId = normalizeEventId(dto.getEventId(), chapterIdx, eventIdx);
                    LocatorDTO startLocator = resolveEventLocator(chapter, startTxtOffset);
                    LocatorDTO endLocator = resolveEventLocator(chapter, endTxtOffset);
                    String rawText = dto.getEventText() != null ? dto.getEventText() : dto.getText();
                    
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
                log.info("파일 '{}' 처리 완료: {}개 이벤트", filename, newEventsFromFile.size());
                
            } catch (IOException e) {
                log.error("이벤트 JSON 파일 파싱 실패: {}", filename, e);
                throw new GeneralException(ErrorStatus.JSON_PARSING_ERROR, "JSON 파일 파싱 실패: " + filename);
            } catch (Exception e) {
                log.error("이벤트 파일 처리 중 오류: {}", filename, e);
                throw new GeneralException(ErrorStatus._INTERNAL_SERVER_ERROR, "이벤트 파일 처리 실패: " + filename);
            }
        }

        // 배치 처리로 저장 (500개씩)
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
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));

        List<CharacterPovSummary> allNewSummaries = new ArrayList<>();
        Pattern pattern = Pattern.compile("chapter(\\d+)_perspective_summaries_Ko\\.json");

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
            try {
                characterPovSummaryRepository.saveAll(allNewSummaries);
                log.info("챕터 요약 {}개 DB 저장 완료", allNewSummaries.size());
            } catch (Exception e) {
                log.error("챕터 요약 DB 저장 실패", e);
                throw new GeneralException(ErrorStatus._INTERNAL_SERVER_ERROR, "챕터 요약 저장 중 데이터베이스 오류가 발생했습니다.");
            }
        }

        // 모든 챕터의 요약이 완료되었는지 확인 후, 책의 전체 요약 상태 업데이트
        try {
            checkAndUpdateBookSummaryStatus(book);
        } catch (Exception e) {
            log.error("책 요약 상태 업데이트 중 오류 발생", e);
            // 책 상태 업데이트 실패는 critical하지 않으므로 로그만 남김
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
        // 입력된 bookId로 Book 엔터티를 조회
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));

        int totalProcessedCount = 0;

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
                totalProcessedCount += processNodeWeights(event, book, dto.getNodeWeightsAccum());
                totalProcessedCount += processRelations(event, book, dto.getRelations());

            } catch (IOException e) {
                throw new GeneralException(ErrorStatus.JSON_PARSING_ERROR, "관계 정보 JSON 파일 파싱에 실패했습니다: " + filename);
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
            // 타입 변환 예외 처리
            Long characterBookId;
            try {
                characterBookId = Long.parseLong(entry.getKey());
            } catch (NumberFormatException e) {
                log.warn("캐릭터 ID 형식 오류, 스킵: {}", entry.getKey());
                continue;
            }
            
            NodeWeightDTO weightDTO = entry.getValue();
            if (weightDTO == null) {
                log.warn("캐릭터 ID {}의 가중치 정보가 null입니다, 스킵", characterBookId);
                continue;
            }

            Optional<Character> characterOpt = characterRepository.findByBookAndCharacterId(book, characterBookId);

            // 캐릭터가 존재할 경우에만 로직을 실행
            if (characterOpt.isPresent()) {
                Character character = characterOpt.get();

                // 1. 기존 데이터가 있는지 확인
                if (statRepository.findByEventAndCharacter(event, character).isPresent()) {
                    throw new GeneralException(ErrorStatus.NODE_WEIGHT_ALREADY_EXISTS,
                            String.format("해당 이벤트에 대한 '%s' 캐릭터의 가중치 데이터가 이미 존재합니다.", character.getName()));
                }

                // 2. 새로운 stat 객체 생성
                EventCharacterStat stat = EventCharacterStat.builder()
                        .event(event)
                        .character(character)
                        .nodeWeight(weightDTO.getWeight())
                        .build();

                log.info("Character {} weight: {}, count: {} (count not stored in EventCharacterStat)",
                        character.getName(), weightDTO.getWeight(), weightDTO.getCount());

                // 3. 리스트에 추가
                newStats.add(stat);
            }
            // 캐릭터가 존재하지 않으면, 이 블록은 실행되지 않고 다음 데이터로 넘어감.
        }

        // 모든 검사가 끝난 후, 한번에 저장
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
            // 필수 필드 검증
            if (dto.getId1() == null || dto.getId2() == null) {
                log.warn("관계 DTO에 캐릭터 ID가 null입니다, 스킵");
                continue;
            }
            
            // 자기 자신과의 관계는 제외
            if (dto.getId1().equals(dto.getId2())) {
                continue;
            }
            
            // Positivity와 Count 검증
            if (dto.getPositivity() == null || dto.getCount() == null) {
                log.warn("관계 DTO에 필수 필드(positivity/count)가 null입니다 ({}->{}), 스킵", dto.getId1(), dto.getId2());
                continue;
            }

            Optional<Character> fromCharOpt = characterRepository.findByBookAndCharacterId(book, dto.getId1());
            Optional<Character> toCharOpt = characterRepository.findByBookAndCharacterId(book, dto.getId2());

            // 두 캐릭터가 모두 존재할 경우에만 아래 로직을 실행
            if (fromCharOpt.isPresent() && toCharOpt.isPresent()) {
                Character fromChar = fromCharOpt.get();
                Character toChar = toCharOpt.get();

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
            // 캐릭터가 하나라도 존재하지 않으면, 이 데이터는 무시하고 다음 루프로 넘어갑니다.
        }

        // 모든 검사가 끝난 후, 한번에 저장
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
        List<Book> books = bookRepository.findBySummaryIsFalse();
        return books.stream()
                .map(book -> BookSummaryDTO.builder()
                        .id(book.getId())
                        .title(book.getTitle())
                        .author(book.getAuthor())
                        .coverImgUrl(book.getCoverImgUrl())
                        .epubPath(book.getEpubPath())
                        .normalizationStatus(book.getNormalizationStatus())
                        .ruleVersion(book.getRuleVersion())
                        .locatorVersion(book.getLocatorVersion())
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
        Matcher matcher = Pattern.compile("^ch(\\d+)-e(\\d+)$").matcher(eventId);
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
        if (!locatorSupport.hasLocatorMetadata(chapter)) {
            return null;
        }
        return locatorSupport.toLocator(chapter, txtOffset);
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

