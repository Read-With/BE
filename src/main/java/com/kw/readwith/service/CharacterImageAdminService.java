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
import com.kw.readwith.domain.enums.CharacterImageFanoutScope;
import com.kw.readwith.domain.enums.CharacterImageGenerationMode;
import com.kw.readwith.domain.enums.CharacterImagePublishPolicy;
import com.kw.readwith.domain.enums.ImageGenerationStatus;
import com.kw.readwith.dto.admin.CharacterImageAssetDTO;
import com.kw.readwith.dto.admin.CharacterImageBookStatusResponseDTO;
import com.kw.readwith.dto.admin.CharacterImageCandidateRequestDTO;
import com.kw.readwith.dto.admin.CharacterImageCharacterStatusDTO;
import com.kw.readwith.dto.admin.CharacterImageFanoutRequestDTO;
import com.kw.readwith.dto.admin.CharacterImageFanoutResponseDTO;
import com.kw.readwith.dto.admin.CharacterImageReferenceCandidateRequestDTO;
import com.kw.readwith.repository.BookCharacterImageProfileRepository;
import com.kw.readwith.repository.BookRepository;
import com.kw.readwith.repository.CharacterImageAssetRepository;
import com.kw.readwith.repository.CharacterRepository;
import com.kw.readwith.service.image.CharacterImageQaService;
import com.kw.readwith.service.image.GeneratedCharacterImage;
import com.kw.readwith.service.image.OpenAiImageEditClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CharacterImageAdminService {

    private static final Set<CharacterImageAssetStatus> REGENERATABLE_FAILURE_STATUSES = Set.of(
            CharacterImageAssetStatus.FAILED,
            CharacterImageAssetStatus.QA_FAILED,
            CharacterImageAssetStatus.REVIEW_REQUIRED
    );

    private static final Set<CharacterImageAssetStatus> PUBLISHED_OR_APPROVED_STATUSES = Set.of(
            CharacterImageAssetStatus.PUBLISHED,
            CharacterImageAssetStatus.APPROVED,
            CharacterImageAssetStatus.QA_PASSED
    );

    private final BookRepository bookRepository;
    private final CharacterRepository characterRepository;
    private final CharacterImageAssetRepository assetRepository;
    private final BookCharacterImageProfileRepository profileRepository;
    private final CharacterImageService characterImageService;
    private final OpenAiImageEditClient imageEditClient;
    private final CharacterImageQaService qaService;
    private final CharacterImageProperties imageProperties;
    private final RestTemplate restTemplate;

    public CharacterImageBookStatusResponseDTO getBookStatus(Long bookId) {
        Book book = getBook(bookId);
        Optional<BookCharacterImageProfile> profile = profileRepository.findByBook(book);
        List<Character> characters = characterRepository.findByBookOrderByIsMainCharacterDescNameAsc(book);

        Map<Long, CharacterImageAsset> latestAssets = assetRepository.findByBookOrderByCreatedAtDesc(book).stream()
                .collect(Collectors.toMap(
                        asset -> asset.getCharacter().getId(),
                        asset -> asset,
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));

        List<CharacterImageCharacterStatusDTO> characterStatuses = characters.stream()
                .map(character -> CharacterImageCharacterStatusDTO.from(character, latestAssets.get(character.getId())))
                .collect(Collectors.toList());

        BookImageReferenceStatus referenceStatus = profile
                .map(BookCharacterImageProfile::getReferenceStatus)
                .orElse(BookImageReferenceStatus.NONE);

        CharacterImageAsset activeReference = profile
                .map(BookCharacterImageProfile::getActiveReferenceAsset)
                .orElse(null);

        Character referenceCharacter = profile
                .map(BookCharacterImageProfile::getReferenceCharacter)
                .orElse(null);

        return CharacterImageBookStatusResponseDTO.builder()
                .bookId(book.getId())
                .referenceStatus(referenceStatus.name())
                .activeReferenceAssetId(activeReference != null ? activeReference.getId() : null)
                .referenceCharacterId(referenceCharacter != null ? referenceCharacter.getId() : null)
                .referenceVersion(profile.map(BookCharacterImageProfile::getReferenceVersion).orElse(0))
                .canFanout(referenceStatus == BookImageReferenceStatus.APPROVED && activeReference != null)
                .characters(characterStatuses)
                .build();
    }

    @Transactional
    public CharacterImageAssetDTO createReferenceCandidate(Long bookId, CharacterImageReferenceCandidateRequestDTO request) {
        Book book = getBook(bookId);
        Character referenceCharacter = resolveReferenceCharacter(book, request != null ? request.getCharacterId() : null);
        BookCharacterImageProfile profile = getOrCreateProfile(book);
        profile.markCandidateGenerating(
                referenceCharacter,
                resolveTextImageModel(),
                sha256(normalize(imageProperties.getBaseStylePrompt())),
                sha256(normalize(book.getBookPrompt()))
        );

        CharacterImageAsset asset = createGeneratingAsset(
                book,
                referenceCharacter,
                CharacterImageAssetRole.REFERENCE_SEED,
                CharacterImageGenerationMode.TEXT_TO_IMAGE,
                null,
                0
        );

        try {
            GeneratedCharacterImage generated = characterImageService.generateTextImage(referenceCharacter);
            String s3Url = characterImageService.uploadGeneratedImage(
                    referenceCharacter,
                    generated.imageData(),
                    characterImageService.buildReferenceCandidateS3KeyName(referenceCharacter, asset.getId())
            );
            asset.generated(s3Url, generated.model(), generated.promptHash(), generated.requestId());
            runQa(asset, true, request == null || request.getAutoQa() == null || request.getAutoQa());

            if (asset.getStatus() == CharacterImageAssetStatus.QA_PASSED) {
                profile.markQaPassed(asset);
            } else if (asset.getStatus() == CharacterImageAssetStatus.QA_FAILED) {
                profile.markQaFailed(referenceCharacter);
            }
        } catch (Exception e) {
            log.error("Reference image candidate generation failed. bookId={}, characterId={}",
                    bookId, referenceCharacter.getId(), e);
            asset.fail("REFERENCE_GENERATION_FAILED");
            profile.markQaFailed(referenceCharacter);
        }

        return CharacterImageAssetDTO.from(asset);
    }

    @Transactional
    public CharacterImageAssetDTO approveReferenceCandidate(Long bookId, Long assetId) {
        Book book = getBook(bookId);
        CharacterImageAsset asset = getAssetInBook(book, assetId);
        ensureAssetRole(asset, CharacterImageAssetRole.REFERENCE_SEED);
        ensurePublishable(asset);

        BookCharacterImageProfile profile = getOrCreateProfile(book);
        CharacterImageAsset previousReference = profile.getActiveReferenceAsset();
        if (profile.getReferenceStatus() == BookImageReferenceStatus.APPROVED
                && previousReference != null
                && previousReference.getId().equals(asset.getId())) {
            if (asset.getStatus() != CharacterImageAssetStatus.PUBLISHED) {
                publishAsset(asset);
            }
            return CharacterImageAssetDTO.from(asset);
        }

        if (previousReference != null && !previousReference.getId().equals(asset.getId())) {
            previousReference.markSuperseded();
            assetRepository.markDerivedAssetsStale(book, previousReference, PUBLISHED_OR_APPROVED_STATUSES);
        }

        profile.approve(asset, "admin");
        publishAsset(asset);
        return CharacterImageAssetDTO.from(asset);
    }

    @Transactional
    public CharacterImageAssetDTO rejectReferenceCandidate(Long bookId, Long assetId) {
        Book book = getBook(bookId);
        CharacterImageAsset asset = getAssetInBook(book, assetId);
        ensureAssetRole(asset, CharacterImageAssetRole.REFERENCE_SEED);
        asset.reject();

        profileRepository.findByBook(book)
                .filter(profile -> profile.getActiveReferenceAsset() != null)
                .filter(profile -> profile.getActiveReferenceAsset().getId().equals(asset.getId()))
                .ifPresent(BookCharacterImageProfile::reject);

        return CharacterImageAssetDTO.from(asset);
    }

    @Transactional
    public CharacterImageFanoutResponseDTO fanout(Long bookId, CharacterImageFanoutRequestDTO request) {
        Book book = getBook(bookId);
        CharacterImageFanoutScope scope = parseScope(request != null ? request.getScope() : null);
        CharacterImagePublishPolicy publishPolicy = parsePublishPolicy(request != null ? request.getPublishPolicy() : null);
        boolean overwritePublished = request != null && Boolean.TRUE.equals(request.getOverwritePublished());
        Integer limit = request != null ? request.getLimit() : null;

        BookCharacterImageProfile profile = profileRepository.findByBook(book)
                .orElseThrow(() -> new GeneralException(ErrorStatus.IMAGE_REFERENCE_NOT_APPROVED));
        if (profile.getReferenceStatus() != BookImageReferenceStatus.APPROVED || profile.getActiveReferenceAsset() == null) {
            throw new GeneralException(ErrorStatus.IMAGE_REFERENCE_NOT_APPROVED);
        }

        List<Character> targets = resolveFanoutTargets(book, scope, request != null ? request.getCharacterIds() : null);
        Character referenceCharacter = profile.getReferenceCharacter();
        if (referenceCharacter != null) {
            targets = targets.stream()
                    .filter(character -> !character.getId().equals(referenceCharacter.getId()))
                    .collect(Collectors.toList());
        }
        if (limit != null && limit > 0 && targets.size() > limit) {
            targets = targets.subList(0, limit);
        }

        List<CharacterImageAssetDTO> generatedAssets = new ArrayList<>();
        int successCount = 0;
        int failedCount = 0;
        int skippedCount = 0;

        for (Character character : targets) {
            if (!overwritePublished && hasPublishedImage(character) && scope != CharacterImageFanoutScope.STALE_ONLY
                    && scope != CharacterImageFanoutScope.FAILED_ONLY) {
                skippedCount++;
                continue;
            }

            CharacterImageAsset asset = createDerivedCandidate(character, profile, publishPolicy);
            generatedAssets.add(CharacterImageAssetDTO.from(asset));
            if (asset.getStatus() == CharacterImageAssetStatus.PUBLISHED
                    || asset.getStatus() == CharacterImageAssetStatus.QA_PASSED) {
                successCount++;
            } else {
                failedCount++;
            }
        }

        return CharacterImageFanoutResponseDTO.builder()
                .scope(scope.name())
                .targetCount(targets.size())
                .successCount(successCount)
                .failedCount(failedCount)
                .skippedCount(skippedCount)
                .assets(generatedAssets)
                .build();
    }

    @Transactional
    public CharacterImageAssetDTO createCharacterCandidate(Long characterId, CharacterImageCandidateRequestDTO request) {
        Character character = getCharacter(characterId);
        Book book = character.getBook();
        CharacterImagePublishPolicy publishPolicy = parsePublishPolicy(request != null ? request.getPublishPolicy() : null);

        Optional<BookCharacterImageProfile> profile = profileRepository.findByBook(book);
        if (profile.isPresent()
                && profile.get().getReferenceStatus() == BookImageReferenceStatus.APPROVED
                && profile.get().getActiveReferenceAsset() != null
                && profile.get().getReferenceCharacter() != null
                && !profile.get().getReferenceCharacter().getId().equals(character.getId())) {
            return CharacterImageAssetDTO.from(createDerivedCandidate(character, profile.get(), publishPolicy));
        }

        CharacterImageAsset asset = createTextCandidate(character, request == null || request.getAutoQa() == null || request.getAutoQa());
        if (publishPolicy == CharacterImagePublishPolicy.AUTO_AFTER_QA && asset.getStatus() == CharacterImageAssetStatus.QA_PASSED) {
            publishAsset(asset);
        }
        return CharacterImageAssetDTO.from(asset);
    }

    @Transactional
    public CharacterImageAssetDTO approveCharacterCandidate(Long characterId, Long assetId) {
        Character character = getCharacter(characterId);
        CharacterImageAsset asset = getAsset(assetId);
        if (!asset.getCharacter().getId().equals(character.getId())) {
            throw new GeneralException(ErrorStatus.IMAGE_ASSET_NOT_BELONG_TO_BOOK);
        }
        ensurePublishable(asset);
        publishAsset(asset);
        return CharacterImageAssetDTO.from(asset);
    }

    @Transactional
    public CharacterImageAssetDTO rejectCharacterCandidate(Long characterId, Long assetId) {
        Character character = getCharacter(characterId);
        CharacterImageAsset asset = getAsset(assetId);
        if (!asset.getCharacter().getId().equals(character.getId())) {
            throw new GeneralException(ErrorStatus.IMAGE_ASSET_NOT_BELONG_TO_BOOK);
        }
        asset.reject();
        return CharacterImageAssetDTO.from(asset);
    }

    private CharacterImageAsset createDerivedCandidate(Character character,
                                                       BookCharacterImageProfile profile,
                                                       CharacterImagePublishPolicy publishPolicy) {
        CharacterImageAsset reference = profile.getActiveReferenceAsset();
        CharacterImageAsset asset = createGeneratingAsset(
                character.getBook(),
                character,
                CharacterImageAssetRole.DERIVED_CHARACTER,
                CharacterImageGenerationMode.REFERENCE_EDIT,
                reference,
                profile.getReferenceVersion()
        );

        try {
            byte[] referenceImage = restTemplate.getForObject(reference.getS3Url(), byte[].class);
            String prompt = buildReferenceEditPrompt(character);
            GeneratedCharacterImage generated = imageEditClient.generate(referenceImage, prompt);
            String s3Url = characterImageService.uploadGeneratedImage(
                    character,
                    generated.imageData(),
                    characterImageService.buildCandidateS3KeyName(character, asset.getId())
            );
            asset.generated(s3Url, generated.model(), generated.promptHash(), generated.requestId());
            runQa(asset, false, true);

            if (publishPolicy == CharacterImagePublishPolicy.AUTO_AFTER_QA
                    && asset.getStatus() == CharacterImageAssetStatus.QA_PASSED) {
                publishAsset(asset);
            }
        } catch (Exception e) {
            log.error("Derived image generation failed. characterId={}, referenceAssetId={}",
                    character.getId(), reference.getId(), e);
            asset.fail("REFERENCE_EDIT_FAILED");
        }

        return asset;
    }

    private CharacterImageAsset createTextCandidate(Character character, boolean autoQa) {
        CharacterImageAsset asset = createGeneratingAsset(
                character.getBook(),
                character,
                CharacterImageAssetRole.STANDALONE_TEXT,
                CharacterImageGenerationMode.TEXT_TO_IMAGE,
                null,
                0
        );

        try {
            GeneratedCharacterImage generated = characterImageService.generateTextImage(character);
            String s3Url = characterImageService.uploadGeneratedImage(
                    character,
                    generated.imageData(),
                    characterImageService.buildCandidateS3KeyName(character, asset.getId())
            );
            asset.generated(s3Url, generated.model(), generated.promptHash(), generated.requestId());
            runQa(asset, false, autoQa);
        } catch (Exception e) {
            log.error("Text image candidate generation failed. characterId={}", character.getId(), e);
            asset.fail("TEXT_IMAGE_GENERATION_FAILED");
        }

        return asset;
    }

    private CharacterImageAsset createGeneratingAsset(Book book,
                                                      Character character,
                                                      CharacterImageAssetRole role,
                                                      CharacterImageGenerationMode generationMode,
                                                      CharacterImageAsset sourceReferenceAsset,
                                                      int referenceVersion) {
        int attemptNo = assetRepository.findMaxAttemptNo(character, role) + 1;
        CharacterImageAsset asset = CharacterImageAsset.builder()
                .book(book)
                .character(character)
                .assetRole(role)
                .generationMode(generationMode)
                .sourceReferenceAsset(sourceReferenceAsset)
                .referenceVersion(referenceVersion)
                .status(CharacterImageAssetStatus.GENERATING)
                .attemptNo(attemptNo)
                .build();
        return assetRepository.save(asset);
    }

    private void runQa(CharacterImageAsset asset, boolean referenceSeed, boolean autoQa) {
        if (!autoQa) {
            return;
        }

        CharacterImageQaService.CharacterImageQaResult result = qaService.review(asset.getS3Url(), referenceSeed);
        if (result.passed()) {
            asset.markQaPassed(result.resultJson());
        } else {
            asset.markQaFailed(result.resultJson(), result.failureCode());
        }
    }

    private void publishAsset(CharacterImageAsset asset) {
        if (asset.getS3Url() == null || asset.getS3Url().isBlank()) {
            throw new GeneralException(ErrorStatus.IMAGE_ASSET_INVALID_STATUS, "게시할 이미지 URL이 없습니다.");
        }
        asset.publish();
        characterRepository.updateProfileImageAndStatus(
                asset.getCharacter().getId(),
                asset.getS3Url(),
                ImageGenerationStatus.COMPLETED
        );
    }

    private List<Character> resolveFanoutTargets(Book book, CharacterImageFanoutScope scope, List<Long> characterIds) {
        List<Character> allCharacters = characterRepository.findByBookOrderByIsMainCharacterDescNameAsc(book);

        return switch (scope) {
            case MAIN_ONLY -> allCharacters.stream()
                    .filter(Character::isMainCharacter)
                    .collect(Collectors.toList());
            case GRAPH_VISIBLE, ALL -> allCharacters;
            case SELECTED -> resolveSelectedCharacters(book, characterIds);
            case STALE_ONLY -> resolveCharactersByAssetStatuses(book, Set.of(
                    CharacterImageAssetStatus.STALE_REFERENCE,
                    CharacterImageAssetStatus.STALE_PROMPT
            ));
            case FAILED_ONLY -> resolveCharactersByAssetStatuses(book, REGENERATABLE_FAILURE_STATUSES);
        };
    }

    private List<Character> resolveSelectedCharacters(Book book, List<Long> characterIds) {
        if (characterIds == null || characterIds.isEmpty()) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "scope=SELECTED일 때 characterIds가 필요합니다.");
        }

        Set<Long> requestedIds = new LinkedHashSet<>(characterIds);
        Map<Long, Character> charactersById = characterRepository.findAllById(requestedIds).stream()
                .collect(Collectors.toMap(Character::getId, character -> character));

        if (charactersById.size() != requestedIds.size()) {
            throw new GeneralException(ErrorStatus.CHARACTER_NOT_FOUND);
        }

        List<Character> result = new ArrayList<>();
        for (Long id : requestedIds) {
            Character character = charactersById.get(id);
            if (character == null || !character.getBook().getId().equals(book.getId())) {
                throw new GeneralException(ErrorStatus.BOOK_CHARACTER_NOT_FOUND);
            }
            result.add(character);
        }
        return result;
    }

    private List<Character> resolveCharactersByAssetStatuses(Book book, Set<CharacterImageAssetStatus> statuses) {
        return assetRepository.findByBookAndStatusIn(book, statuses).stream()
                .sorted(Comparator.comparing(CharacterImageAsset::getCreatedAt).reversed())
                .map(CharacterImageAsset::getCharacter)
                .filter(Objects::nonNull)
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(Character::getId, character -> character, (first, ignored) -> first, LinkedHashMap::new),
                        map -> new ArrayList<>(map.values())
                ));
    }

    private Character resolveReferenceCharacter(Book book, Long requestedCharacterId) {
        if (requestedCharacterId != null) {
            Character character = getCharacter(requestedCharacterId);
            if (!character.getBook().getId().equals(book.getId())) {
                throw new GeneralException(ErrorStatus.BOOK_CHARACTER_NOT_FOUND);
            }
            return character;
        }

        return characterRepository.findByBookOrderByIsMainCharacterDescNameAsc(book).stream()
                .findFirst()
                .orElseThrow(() -> new GeneralException(ErrorStatus.IMAGE_REFERENCE_CHARACTER_REQUIRED));
    }

    private CharacterImageFanoutScope parseScope(String rawScope) {
        String normalized = rawScope == null || rawScope.isBlank() ? "MAIN_ONLY" : rawScope.trim();
        try {
            return CharacterImageFanoutScope.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new GeneralException(ErrorStatus.INVALID_IMAGE_FANOUT_SCOPE);
        }
    }

    private CharacterImagePublishPolicy parsePublishPolicy(String rawPolicy) {
        String normalized = rawPolicy == null || rawPolicy.isBlank() ? "MANUAL" : rawPolicy.trim();
        try {
            return CharacterImagePublishPolicy.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new GeneralException(ErrorStatus.INVALID_IMAGE_PUBLISH_POLICY);
        }
    }

    private void ensureAssetRole(CharacterImageAsset asset, CharacterImageAssetRole expectedRole) {
        if (asset.getAssetRole() != expectedRole) {
            throw new GeneralException(ErrorStatus.IMAGE_ASSET_INVALID_STATUS,
                    "요청한 작업에 사용할 수 없는 이미지 asset role입니다.");
        }
    }

    private void ensurePublishable(CharacterImageAsset asset) {
        if (asset.getStatus() != CharacterImageAssetStatus.QA_PASSED
                && asset.getStatus() != CharacterImageAssetStatus.APPROVED
                && asset.getStatus() != CharacterImageAssetStatus.PUBLISHED) {
            throw new GeneralException(ErrorStatus.IMAGE_ASSET_INVALID_STATUS,
                    "QA를 통과한 이미지 후보만 승인/게시할 수 있습니다.");
        }
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

    private Book getBook(Long bookId) {
        return bookRepository.findById(bookId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));
    }

    private Character getCharacter(Long characterId) {
        return characterRepository.findByIdWithBook(characterId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.CHARACTER_NOT_FOUND));
    }

    private CharacterImageAsset getAsset(Long assetId) {
        return assetRepository.findById(assetId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.IMAGE_ASSET_NOT_FOUND));
    }

    private CharacterImageAsset getAssetInBook(Book book, Long assetId) {
        return assetRepository.findByIdAndBook(assetId, book)
                .orElseThrow(() -> new GeneralException(ErrorStatus.IMAGE_ASSET_NOT_BELONG_TO_BOOK));
    }

    private boolean hasPublishedImage(Character character) {
        return character.getProfileImage() != null && !character.getProfileImage().isBlank();
    }

    private String buildReferenceEditPrompt(Character character) {
        return "Use the input image as a style reference only. Keep the same illustration style, framing, " +
                "background simplicity, color palette, and rendering quality. Create a distinct person matching " +
                "the target character profile. Do not copy the reference person's facial identity, age, gender, " +
                "ethnicity, clothing, or exact features unless explicitly specified. " +
                characterImageService.buildImagePrompt(character);
    }

    private String resolveTextImageModel() {
        String configured = normalize(imageProperties.getModel());
        return configured != null ? configured : "gpt-image-1";
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String sha256(String value) {
        if (value == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available.", e);
        }
    }
}
