package com.kw.readwith.service;

import com.kw.readwith.aws.s3.AmazonS3Manager;
import com.kw.readwith.config.CharacterImageProperties;
import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.Character;
import com.kw.readwith.domain.enums.ImageGenerationStatus;
import com.kw.readwith.repository.CharacterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.openai.OpenAiImageModel;
import org.springframework.ai.openai.OpenAiImageOptions;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CharacterImageService {

    private static final String DEFAULT_BASE_STYLE_PROMPT =
            "A single centered chest-up character portrait in a mature editorial gouache illustration style, " +
            "flat matte gouache rendering, clean silhouette, minimal linework, soft paper texture, plain muted background, " +
            "low-saturation palette, restrained literary mood, natural facial proportions, not cartoonish, not anime, " +
            "not painterly, not photorealistic";

    private static final String PROFILE_FORMAT_ENFORCEMENT =
            "exactly one character, single centered chest-up bust portrait, one subject filling most of the frame, " +
            "front-facing or slight three-quarter view, plain soft background, simple profile-card composition";

    private static final String NEGATIVE_LAYOUT_ENFORCEMENT =
            "no additional people, no duo, no pair, no group, no crowd, no background characters, " +
            "no split composition, no diptych, no mirrored duplicate, no reflected second face, no twin, " +
            "no interaction scene, no full body, no wide shot, no props, no scenery, no ornate background";

    private static final List<String> DISALLOWED_PROMPT_SEGMENTS = List.of(
            "duo portrait",
            "pair portrait",
            "group portrait",
            "double portrait",
            "multiple people",
            "multiple characters",
            "multiple figures",
            "two people",
            "two characters",
            "two figures",
            "crowd behind",
            "background characters",
            "split composition",
            "diptych",
            "mirrored second portrait",
            "mirror image",
            "reflected second face",
            "scene with another character",
            "interaction scene",
            "standing beside another character",
            "next to another character"
    );

    private final OpenAiImageModel imageModel;
    private final AmazonS3Manager s3Manager;
    private final CharacterRepository characterRepository;
    private final CharacterImageProperties imageProperties;
    private final CharacterImageTransactionService transactionService;

    @Async("imageGenerationExecutor")
    public CompletableFuture<Void> generateImagesAsync(List<Long> characterIds) {
        int totalCount = characterIds.size();
        log.info("Starting character image generation. totalCount={}, batchSize={}",
                totalCount, imageProperties.getBatchSize());

        int processedCount = 0;
        int successCount = 0;
        int failedCount = 0;
        int skippedCount = 0;

        for (int i = 0; i < characterIds.size(); i++) {
            Long characterId = characterIds.get(i);
            processedCount++;

            try {
                Character character = characterRepository.findByIdWithBook(characterId)
                        .orElseThrow(() -> new RuntimeException("Character not found: " + characterId));

                if (character.getProfileImage() != null
                        && !character.getProfileImage().isEmpty()
                        && character.getImageGenerationStatus() == ImageGenerationStatus.COMPLETED) {
                    log.info("Skipping character image generation because it already exists. characterId={}, name={}",
                            characterId, character.getName());
                    skippedCount++;
                    continue;
                }

                transactionService.updateStatus(characterId, ImageGenerationStatus.PENDING);
                generateAndSaveImage(characterId);
                successCount++;

                if (i < characterIds.size() - 1) {
                    Thread.sleep(imageProperties.getDelayBetweenRequestsMs());
                }

                if (processedCount % imageProperties.getBatchSize() == 0) {
                    log.info("Character image generation progress. processed={}, total={}, success={}, failed={}, skipped={}",
                            processedCount, totalCount, successCount, failedCount, skippedCount);
                }
            } catch (InterruptedException e) {
                log.error("Character image generation interrupted. characterId={}", characterId, e);
                Thread.currentThread().interrupt();
                failedCount++;
                saveFallbackImage(characterId);
                break;
            } catch (Exception e) {
                log.error("Character image generation failed. characterId={}", characterId, e);
                failedCount++;
                saveFallbackImage(characterId);
            }
        }

        log.info("Character image generation completed. total={}, success={}, failed={}, skipped={}",
                totalCount, successCount, failedCount, skippedCount);

        return CompletableFuture.completedFuture(null);
    }

    public void generateAndSaveImage(Long characterId) {
        log.info("Generating character image. characterId={}", characterId);

        try {
            Character character = getCharacterReadOnly(characterId);
            transactionService.updateStatus(characterId, ImageGenerationStatus.GENERATING);

            String prompt = buildDallePrompt(character);
            log.debug("Generated image prompt for characterId={}: {}", characterId, prompt);

            ImageResponse response = imageModel.call(
                    new ImagePrompt(
                            prompt,
                            OpenAiImageOptions.builder()
                                    .withModel("dall-e-3")
                                    .withQuality("standard")
                                    .withN(1)
                                    .withHeight(1024)
                                    .withWidth(1024)
                                    .build()
                    )
            );

            String dalleImageUrl = response.getResult().getOutput().getUrl();
            byte[] imageData = downloadImageFromUrl(dalleImageUrl);
            String base64Image = Base64.getEncoder().encodeToString(imageData);

            String s3KeyName = buildS3KeyName(character);
            String s3Url = s3Manager.uploadFileFromBase64(s3KeyName, base64Image, "image/png");
            updateImageUrlOnly(characterId, s3Url);

            log.info("Character image generation completed. characterId={}, s3Url={}", characterId, s3Url);
        } catch (Exception e) {
            log.error("Character image generation failed during processing. characterId={}", characterId, e);
            transactionService.updateStatus(characterId, ImageGenerationStatus.FAILED);
            throw new RuntimeException("Failed to generate character image: " + characterId, e);
        }
    }

    @Transactional(readOnly = true)
    private Character getCharacterReadOnly(Long characterId) {
        return characterRepository.findByIdWithBook(characterId)
                .orElseThrow(() -> new RuntimeException("Character not found: " + characterId));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    private void updateImageUrlOnly(Long characterId, String s3Url) {
        transactionService.updateImageAndStatus(characterId, s3Url, ImageGenerationStatus.COMPLETED);
    }

    private String buildDallePrompt(Character character) {
        StringBuilder prompt = new StringBuilder();

        appendPromptSegment(prompt, resolveBaseStylePrompt());
        appendPromptSegment(prompt, PROFILE_FORMAT_ENFORCEMENT);
        appendPromptSegment(prompt, sanitizePromptSegment(resolveBookPrompt(character.getBook())));
        appendPromptSegment(prompt, sanitizePromptSegment(resolvePortraitPrompt(character)));
        appendPromptSegment(prompt, NEGATIVE_LAYOUT_ENFORCEMENT);

        return prompt.toString();
    }

    private String resolveBookPrompt(Book book) {
        return book == null ? null : book.getBookPrompt();
    }

    private String resolvePortraitPrompt(Character character) {
        String portraitPrompt = normalizePromptSegment(character.getProfileText());
        if (portraitPrompt != null) {
            return portraitPrompt;
        }
        return "single centered chest-up bust portrait of " + character.getName();
    }

    private String resolveBaseStylePrompt() {
        String configured = normalizePromptSegment(imageProperties.getBaseStylePrompt());
        return configured != null ? configured : DEFAULT_BASE_STYLE_PROMPT;
    }

    private String sanitizePromptSegment(String value) {
        String normalized = normalizePromptSegment(value);
        if (normalized == null) {
            return null;
        }

        List<String> sanitizedSegments = List.of(normalized.split("\\s*[,;]+\\s*")).stream()
                .map(this::normalizePromptSegment)
                .filter(segment -> segment != null && !containsDisallowedPromptSegment(segment))
                .collect(Collectors.toList());

        if (sanitizedSegments.isEmpty()) {
            return null;
        }

        return String.join(", ", sanitizedSegments);
    }

    private boolean containsDisallowedPromptSegment(String segment) {
        String lowerSegment = segment.toLowerCase(Locale.ROOT);
        return DISALLOWED_PROMPT_SEGMENTS.stream()
                .anyMatch(lowerSegment::contains);
    }

    private String normalizePromptSegment(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value
                .replace("\r\n", " ")
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replaceAll("\\s+", " ")
                .trim();

        return normalized.isEmpty() ? null : normalized;
    }

    private void appendPromptSegment(StringBuilder prompt, String segment) {
        if (segment == null || segment.isBlank()) {
            return;
        }

        if (prompt.length() > 0) {
            prompt.append(", ");
        }
        prompt.append(segment);
    }

    private byte[] downloadImageFromUrl(String imageUrl) throws IOException {
        log.debug("Downloading generated image from URL={}", imageUrl);

        try {
            URL url = new URL(imageUrl);
            try (InputStream inputStream = url.openStream()) {
                byte[] imageData = inputStream.readAllBytes();
                if (imageData.length == 0) {
                    throw new IOException("Downloaded image is empty.");
                }
                return imageData;
            }
        } catch (IOException e) {
            log.error("Generated image download failed. url={}", imageUrl, e);
            throw new IOException("Failed to download generated image: " + e.getMessage(), e);
        }
    }

    private String buildS3KeyName(Character character) {
        return String.format("%s/%d/%d.png",
                imageProperties.getS3Path(),
                character.getBook().getId(),
                character.getId());
    }

    private void saveFallbackImage(Long characterId) {
        try {
            transactionService.updateImageAndStatus(
                    characterId,
                    imageProperties.getFallbackUrl(),
                    ImageGenerationStatus.FAILED
            );
            log.info("Stored fallback image for characterId={}", characterId);
        } catch (Exception e) {
            log.error("Failed to store fallback image. characterId={}", characterId, e);
        }
    }
}
