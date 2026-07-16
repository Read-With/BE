package com.kw.readwith.service;

import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.config.CharacterImageProperties;
import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.BookCharacterImageProfile;
import com.kw.readwith.domain.Character;
import com.kw.readwith.domain.CharacterImageAsset;
import com.kw.readwith.domain.enums.BookImageReferenceStatus;
import com.kw.readwith.domain.enums.CharacterImageAssetRole;
import com.kw.readwith.domain.enums.CharacterImageAssetStatus;
import com.kw.readwith.domain.enums.CharacterImageGenerationMode;
import com.kw.readwith.domain.enums.ImageGenerationStatus;
import com.kw.readwith.domain.enums.ProcessingPipelineType;
import com.kw.readwith.domain.enums.ProcessingJobStatus;
import com.kw.readwith.dto.admin.AdminImageGenerationStatusResponseDTO;
import com.kw.readwith.dto.admin.ProcessingJobResponseDTO;
import com.kw.readwith.repository.BookCharacterImageProfileRepository;
import com.kw.readwith.repository.BookRepository;
import com.kw.readwith.repository.CharacterImageAssetRepository;
import com.kw.readwith.repository.CharacterRepository;
import com.kw.readwith.repository.ProcessingJobRepository;
import com.kw.readwith.service.image.GeneratedCharacterImage;
import com.kw.readwith.service.image.OpenAiImageEditClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminImageGenerationService {

    private final BookRepository bookRepository;
    private final CharacterRepository characterRepository;
    private final CharacterImageAssetRepository assetRepository;
    private final BookCharacterImageProfileRepository profileRepository;
    private final CharacterImageService characterImageService;
    private final OpenAiImageEditClient imageEditClient;
    private final CharacterImageProperties imageProperties;
    private final CharacterImageFanoutJobService fanoutJobService;
    private final ReferenceCandidateJobService referenceCandidateJobService;
    private final ProcessingJobRepository processingJobRepository;
    private final RestTemplate restTemplate;
    private final CdnUrlService cdnUrlService;

    public AdminImageGenerationStatusResponseDTO getBookStatus(Long bookId) {
        Book book = getBook(bookId);
        return buildStatusResponse(book);
    }

    @Transactional
    public AdminImageGenerationStatusResponseDTO generateReferenceCandidates(Long bookId) {
        Book book = getBook(bookId);
        ensureNoActiveFanoutJob(bookId);
        Character referenceCharacter = resolveReferenceCharacter(book)
                .orElseThrow(() -> new GeneralException(ErrorStatus.IMAGE_REFERENCE_CHARACTER_REQUIRED));
        referenceCandidateJobService.queue(book, referenceCharacter);
        return buildStatusResponse(book);
    }

    @Transactional
    public AdminImageGenerationStatusResponseDTO selectReferenceCandidate(Long bookId, Long candidateId) {
        Book book = getBook(bookId);
        ensureNoActiveFanoutJob(bookId);
        referenceCandidateJobService.ensureNoActiveJob(bookId);
        CharacterImageAsset candidate = assetRepository.findByIdAndBook(candidateId, book)
                .orElseThrow(() -> new GeneralException(ErrorStatus.IMAGE_ASSET_NOT_BELONG_TO_BOOK));
        ensureReferenceCandidate(candidate);
        ensureReadyReferenceCandidate(candidate);

        BookCharacterImageProfile profile = getOrCreateProfile(book);
        String previousProfileImageUrl = candidate.getCharacter().getProfileImage();
        profile.selectReferenceCandidate(candidate, "admin");
        candidate.publish();
        characterRepository.updateProfileImageAndStatus(
                candidate.getCharacter().getId(),
                candidate.getS3Url(),
                ImageGenerationStatus.COMPLETED
        );
        characterImageService.deleteReplacedGeneratedImage(previousProfileImageUrl, candidate.getS3Url());

        fanoutJobService.queueFanout(book, profile, candidate);
        return buildStatusResponse(book);
    }

    @Transactional
    public AdminImageGenerationStatusResponseDTO regenerateCharacterImage(Long bookId, Long characterId) {
        Book book = getBook(bookId);
        ensureNoActiveFanoutJob(bookId);
        referenceCandidateJobService.ensureNoActiveJob(bookId);
        Character character = getCharacter(characterId);
        if (!character.getBook().getId().equals(book.getId())) {
            throw new GeneralException(ErrorStatus.BOOK_CHARACTER_NOT_FOUND);
        }

        BookCharacterImageProfile profile = getApprovedProfile(book);
        CharacterImageAsset reference = profile.getActiveReferenceAsset();
        if (profile.getReferenceCharacter() != null
                && profile.getReferenceCharacter().getId().equals(character.getId())) {
            throw new GeneralException(
                    ErrorStatus.IMAGE_ASSET_INVALID_STATUS,
                    "대표 캐릭터 이미지는 후보사진을 재생성한 뒤 다시 선택해야 합니다."
            );
        }

        generateCharacterImageFromReference(character, reference, profile.getReferenceVersion());
        return buildStatusResponse(book);
    }

    private CharacterImageAsset generateCharacterImageFromReference(Character character,
                                                                    CharacterImageAsset reference,
                                                                    int referenceVersion) {
        CharacterImageAsset asset = getOrCreateCharacterImageAsset(character, reference, referenceVersion);

        try {
            String previousS3Url = asset.getS3Url();
            byte[] referenceImage = restTemplate.getForObject(cdnUrlService.toPublicUrl(reference.getS3Url()), byte[].class);
            String prompt = characterImageService.buildReferenceEditPrompt(character);
            GeneratedCharacterImage generated = imageEditClient.generate(referenceImage, prompt);
            String s3Url = characterImageService.uploadGeneratedImage(
                    character,
                    generated.imageData(),
                    characterImageService.buildPublishedS3KeyName(
                            character,
                            asset.getReferenceVersion(),
                            asset.getAttemptNo()
                    )
            );
            asset.generated(s3Url, generated.model(), generated.promptHash(), generated.requestId());
            asset.markQaPassed("{\"passed\":true,\"mode\":\"ADMIN_CHARACTER_FANOUT\"}");
            asset.publish();
            characterRepository.updateProfileImageAndStatus(
                    character.getId(),
                    s3Url,
                    ImageGenerationStatus.COMPLETED
            );
            characterImageService.deleteReplacedGeneratedImage(previousS3Url, s3Url);
        } catch (Exception e) {
            log.error("Character image fan-out failed. characterId={}, referenceAssetId={}",
                    character.getId(), reference.getId(), e);
            asset.fail("REFERENCE_EDIT_FAILED");
            characterRepository.updateImageGenerationStatus(character.getId(), ImageGenerationStatus.FAILED);
        }

        return asset;
    }

    private AdminImageGenerationStatusResponseDTO buildStatusResponse(Book book) {
        Optional<BookCharacterImageProfile> profile = profileRepository.findByBook(book);
        Long selectedReferenceCandidateId = profile
                .map(BookCharacterImageProfile::getActiveReferenceAsset)
                .map(CharacterImageAsset::getId)
                .orElse(null);
        int candidateCount = referenceCandidateCount();
        List<Character> characters = characterRepository.findByBookOrderByIsMainCharacterDescNameAsc(book);
        List<CharacterImageAsset> referenceCandidates = assetRepository
                .findByBookAndAssetRoleOrderBySlotNoAscCreatedAtAsc(book, CharacterImageAssetRole.REFERENCE_CANDIDATE)
                .stream()
                .filter(asset -> asset.getSlotNo() != null
                        && asset.getSlotNo() >= 1
                        && (asset.getSlotNo() <= candidateCount
                        || (selectedReferenceCandidateId != null
                        && selectedReferenceCandidateId.equals(asset.getId()))))
                .sorted(Comparator.comparing(
                        CharacterImageAsset::getSlotNo,
                        Comparator.nullsLast(Integer::compareTo)
                ))
                .toList();

        Map<Long, CharacterImageAsset> characterAssets = assetRepository
                .findByBookAndAssetRoleOrderByCreatedAtDesc(book, CharacterImageAssetRole.CHARACTER_IMAGE)
                .stream()
                .collect(Collectors.toMap(
                        asset -> asset.getCharacter().getId(),
                        asset -> asset,
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));

        Character referenceCharacter = profile
                .map(BookCharacterImageProfile::getReferenceCharacter)
                .or(() -> resolveReferenceCharacter(book))
                .orElse(null);
        Long referenceCharacterId = referenceCharacter != null ? referenceCharacter.getId() : null;

        String status = resolveBookStatus(profile, referenceCandidates, characterAssets);
        ProcessingJobResponseDTO fanoutJob = processingJobRepository
                .findTopByBookIdAndPipelineTypeOrderByCreatedAtDesc(
                        book.getId(),
                        ProcessingPipelineType.IMAGE_GENERATION
                )
                .map(ProcessingJobResponseDTO::from)
                .orElse(null);
        ProcessingJobResponseDTO referenceCandidateJob = processingJobRepository
                .findTopByBookIdAndPipelineTypeOrderByCreatedAtDesc(
                        book.getId(),
                        ProcessingPipelineType.IMAGE_REFERENCE_GENERATION
                )
                .map(ProcessingJobResponseDTO::from)
                .orElse(null);

        return AdminImageGenerationStatusResponseDTO.builder()
                .bookId(book.getId())
                .status(status)
                .nextAction(resolveNextAction(status))
                .referenceCandidateJob(referenceCandidateJob)
                .fanoutJob(fanoutJob)
                .referenceCharacter(AdminImageGenerationStatusResponseDTO.CharacterSummary.from(referenceCharacter))
                .selectedReferenceCandidateId(selectedReferenceCandidateId)
                .referenceCandidates(referenceCandidates.stream()
                        .map(asset -> AdminImageGenerationStatusResponseDTO.ReferenceCandidate.from(
                                asset,
                                selectedReferenceCandidateId,
                                cdnUrlService
                        ))
                        .toList())
                .characters(characters.stream()
                        .map(character -> AdminImageGenerationStatusResponseDTO.CharacterImage.from(
                                character,
                                characterAssets.get(character.getId()),
                                referenceCharacterId,
                                cdnUrlService
                        ))
                        .toList())
                .build();
    }

    private String resolveBookStatus(Optional<BookCharacterImageProfile> profile,
                                     List<CharacterImageAsset> referenceCandidates,
                                     Map<Long, CharacterImageAsset> characterAssets) {
        if (referenceCandidates.stream().anyMatch(asset -> asset.getStatus() == CharacterImageAssetStatus.GENERATING
                || asset.getStatus() == CharacterImageAssetStatus.QA_PENDING)) {
            return "REFERENCE_GENERATING";
        }
        if (profile.isEmpty() && referenceCandidates.isEmpty()) {
            return "EMPTY";
        }
        if (profile.map(BookCharacterImageProfile::getReferenceStatus).orElse(BookImageReferenceStatus.NONE)
                == BookImageReferenceStatus.QA_FAILED) {
            return "FAILED";
        }
        if (profile.map(BookCharacterImageProfile::getActiveReferenceAsset).isEmpty()) {
            return referenceCandidates.isEmpty() ? "EMPTY" : "REFERENCE_READY";
        }
        if (characterAssets.values().stream().anyMatch(asset -> asset.getStatus() == CharacterImageAssetStatus.GENERATING
                || asset.getStatus() == CharacterImageAssetStatus.QA_PENDING)) {
            return "FANOUT_GENERATING";
        }
        if (characterAssets.values().stream().anyMatch(asset -> asset.getStatus() == CharacterImageAssetStatus.FAILED
                || asset.getStatus() == CharacterImageAssetStatus.QA_FAILED)) {
            return "FAILED";
        }
        return "READY";
    }

    private String resolveNextAction(String status) {
        return switch (status) {
            case "EMPTY", "FAILED" -> "GENERATE_REFERENCE_CANDIDATES";
            case "REFERENCE_GENERATING" -> "WAIT_REFERENCE_CANDIDATES";
            case "REFERENCE_READY" -> "SELECT_REFERENCE_CANDIDATE";
            case "FANOUT_GENERATING" -> "WAIT_FANOUT";
            default -> "REVIEW_OR_REGENERATE_CHARACTER_IMAGES";
        };
    }

    private CharacterImageAsset getOrCreateCharacterImageAsset(Character character,
                                                               CharacterImageAsset reference,
                                                               int referenceVersion) {
        Optional<CharacterImageAsset> existing = assetRepository
                .findFirstByCharacterAndAssetRoleOrderByCreatedAtDesc(
                        character,
                        CharacterImageAssetRole.CHARACTER_IMAGE
                );
        if (existing.isPresent()) {
            CharacterImageAsset asset = existing.get();
            asset.beginCharacterImage(character, reference, referenceVersion);
            return asset;
        }

        return assetRepository.save(CharacterImageAsset.builder()
                .book(character.getBook())
                .character(character)
                .assetRole(CharacterImageAssetRole.CHARACTER_IMAGE)
                .generationMode(CharacterImageGenerationMode.REFERENCE_EDIT)
                .sourceReferenceAsset(reference)
                .referenceVersion(referenceVersion)
                .status(CharacterImageAssetStatus.GENERATING)
                .attemptNo(1)
                .build());
    }

    private BookCharacterImageProfile getApprovedProfile(Book book) {
        BookCharacterImageProfile profile = profileRepository.findByBook(book)
                .orElseThrow(() -> new GeneralException(ErrorStatus.IMAGE_REFERENCE_NOT_APPROVED));
        if (profile.getReferenceStatus() != BookImageReferenceStatus.APPROVED
                || profile.getActiveReferenceAsset() == null
                || profile.getActiveReferenceAsset().getS3Url() == null
                || profile.getActiveReferenceAsset().getS3Url().isBlank()) {
            throw new GeneralException(ErrorStatus.IMAGE_REFERENCE_NOT_APPROVED);
        }
        return profile;
    }

    private BookCharacterImageProfile getOrCreateProfile(Book book) {
        return profileRepository.findByBook(book)
                .orElseGet(() -> profileRepository.save(
                        BookCharacterImageProfile.builder()
                                .book(book)
                                .referenceStatus(BookImageReferenceStatus.NONE)
                                .build()
                ));
    }

    private Optional<Character> resolveReferenceCharacter(Book book) {
        return characterRepository.findByBookOrderByIsMainCharacterDescNameAsc(book).stream()
                .findFirst();
    }

    private Book getBook(Long bookId) {
        return bookRepository.findById(bookId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));
    }

    private Character getCharacter(Long characterId) {
        return characterRepository.findByIdWithBook(characterId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.CHARACTER_NOT_FOUND));
    }

    private void ensureNoActiveFanoutJob(Long bookId) {
        processingJobRepository.findFirstByBookIdAndPipelineTypeAndStatusInOrderByCreatedAtDesc(
                        bookId,
                        ProcessingPipelineType.IMAGE_GENERATION,
                        EnumSet.of(ProcessingJobStatus.QUEUED, ProcessingJobStatus.PROCESSING)
                )
                .ifPresent(job -> {
                    throw new GeneralException(ErrorStatus.IMAGE_FANOUT_JOB_ACTIVE);
                });
    }

    private void ensureReferenceCandidate(CharacterImageAsset asset) {
        if (asset.getAssetRole() != CharacterImageAssetRole.REFERENCE_CANDIDATE) {
            throw new GeneralException(
                    ErrorStatus.IMAGE_ASSET_INVALID_STATUS,
                    "선택한 asset은 대표 후보사진이 아닙니다."
            );
        }
    }

    private void ensureReadyReferenceCandidate(CharacterImageAsset asset) {
        if (asset.getS3Url() == null || asset.getS3Url().isBlank()
                || !isReadyForSelection(asset.getStatus())) {
            throw new GeneralException(
                    ErrorStatus.IMAGE_ASSET_INVALID_STATUS,
                    "선택할 대표 후보사진은 생성이 완료된 READY 상태여야 합니다."
            );
        }
    }

    private boolean isReadyForSelection(CharacterImageAssetStatus status) {
        return status == CharacterImageAssetStatus.QA_PASSED
                || status == CharacterImageAssetStatus.APPROVED
                || status == CharacterImageAssetStatus.PUBLISHED;
    }

    private int referenceCandidateCount() {
        return Math.min(Math.max(imageProperties.getReferenceCandidateCount(), 1), 4);
    }
}
