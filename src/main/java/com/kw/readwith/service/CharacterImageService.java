package com.kw.readwith.service;

import com.kw.readwith.aws.s3.AmazonS3Manager;
import com.kw.readwith.config.CharacterImageProperties;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 캐릭터 프로필 이미지 생성 및 관리 서비스
 * OpenAI DALL-E 3를 사용하여 캐릭터의 외모 묘사를 기반으로 이미지를 생성
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CharacterImageService {

    private final OpenAiImageModel imageModel;
    private final AmazonS3Manager s3Manager;
    private final CharacterRepository characterRepository;
    private final CharacterImageProperties imageProperties;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 여러 캐릭터의 이미지를 비동기로 생성
     * 
     * @param characters 이미지를 생성할 캐릭터 리스트
     * @return 비동기 작업 결과
     */
    @Async("imageGenerationExecutor")
    public CompletableFuture<Void> generateImagesAsync(List<Character> characters) {
        log.info("캐릭터 이미지 생성 시작 - 총 {}명", characters.size());
        
        for (Character character : characters) {
            try {
                // 이미 이미지가 있거나 생성 완료된 경우 스킵
                if (character.getProfileImage() != null && !character.getProfileImage().isEmpty() 
                    && character.getImageGenerationStatus() == ImageGenerationStatus.COMPLETED) {
                    log.info("캐릭터 '{}' - 이미지가 이미 존재하여 스킵", character.getName());
                    continue;
                }

                // 상태를 PENDING으로 설정
                updateStatus(character.getId(), ImageGenerationStatus.PENDING);
                
                generateAndSaveImage(character);
                
            } catch (Exception e) {
                log.error("캐릭터 '{}' 이미지 생성 실패: {}", character.getName(), e.getMessage(), e);
                // 실패 시 폴백 이미지 설정
                saveFallbackImage(character);
            }
        }
        
        log.info("캐릭터 이미지 생성 완료");
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 단일 캐릭터의 이미지를 생성하고 저장
     * 
     * @param character 이미지를 생성할 캐릭터
     */
    @Transactional
    public void generateAndSaveImage(Character character) {
        log.info("캐릭터 '{}' 이미지 생성 시작", character.getName());

        try {
            // 상태를 GENERATING으로 변경
            updateStatus(character.getId(), ImageGenerationStatus.GENERATING);

            // 1. 프롬프트 생성
            String prompt = buildDallePrompt(character);
            log.debug("생성된 프롬프트: {}", prompt);

            // 2. DALL-E API 호출
            ImageResponse response = imageModel.call(
                new ImagePrompt(prompt, 
                    OpenAiImageOptions.builder()
                        .withModel("dall-e-3")
                        .withQuality("standard")
                        .withN(1)
                        .withHeight(1024)
                        .withWidth(1024)
                        .build()
                )
            );

            // 3. 생성된 이미지 URL 추출
            String dalleImageUrl = response.getResult().getOutput().getUrl();
            log.info("캐릭터 '{}' DALL-E 이미지 생성 완료: {}", character.getName(), dalleImageUrl);

            // 4. DALL-E URL에서 이미지 다운로드
            byte[] imageData = downloadImageFromUrl(dalleImageUrl);
            log.info("캐릭터 '{}' 이미지 다운로드 완료 - 크기: {} bytes", character.getName(), imageData.length);

            // 5. 이미지를 Base64로 인코딩하여 S3에 업로드
            String base64Image = Base64.getEncoder().encodeToString(imageData);
            String s3KeyName = buildS3KeyName(character);
            String s3Url = s3Manager.uploadFileFromBase64(s3KeyName, base64Image, "image/png");
            
            log.info("캐릭터 '{}' S3 업로드 완료: {}", character.getName(), s3Url);
            
            // 6. Character 엔티티 업데이트 (URL과 상태를 COMPLETED로)
            updateCharacterImageAndStatus(character.getId(), s3Url, ImageGenerationStatus.COMPLETED);
            
            log.info("캐릭터 '{}' 이미지 생성 및 저장 완료", character.getName());

        } catch (Exception e) {
            log.error("캐릭터 '{}' 이미지 생성 중 오류 발생", character.getName(), e);
            updateStatus(character.getId(), ImageGenerationStatus.FAILED);
            throw e;
        }
    }

    /**
     * DALL-E 프롬프트 생성
     * 캐릭터의 profileText와 공통 스타일을 결합
     * 
     * @param character 대상 캐릭터
     * @return 생성된 프롬프트
     */
    private String buildDallePrompt(Character character) {
        StringBuilder prompt = new StringBuilder();
        
        // 캐릭터 외모 묘사 추가
        if (character.getProfileText() != null && !character.getProfileText().isEmpty()) {
            prompt.append(character.getProfileText()).append(". ");
        } else {
            // profileText가 없는 경우 이름만 사용
            prompt.append("A character named ").append(character.getName()).append(". ");
        }
        
        // 공통 아트 스타일 추가
        prompt.append(imageProperties.getCommonStyle());
        
        return prompt.toString();
    }

    /**
     * URL에서 이미지를 다운로드하여 byte 배열로 반환
     * 
     * @param imageUrl 다운로드할 이미지 URL
     * @return 이미지 데이터 (byte 배열)
     * @throws IOException 다운로드 실패 시
     */
    private byte[] downloadImageFromUrl(String imageUrl) throws IOException {
        log.debug("이미지 다운로드 시작: {}", imageUrl);
        byte[] imageData = restTemplate.getForObject(imageUrl, byte[].class);
        
        if (imageData == null || imageData.length == 0) {
            throw new IOException("다운로드한 이미지 데이터가 비어있습니다.");
        }
        
        return imageData;
    }

    /**
     * S3 저장을 위한 키 이름 생성
     * 형식: {s3Path}/{bookId}/{characterId}.png
     * 
     * @param character 대상 캐릭터
     * @return S3 키 이름
     */
    private String buildS3KeyName(Character character) {
        return String.format("%s/%d/%d.png", 
            imageProperties.getS3Path(),
            character.getBook().getId(),
            character.getId()
        );
    }

    /**
     * 캐릭터의 이미지 생성 상태만 업데이트
     * 
     * @param characterId 캐릭터 ID
     * @param status 변경할 상태
     */
    @Transactional
    protected void updateStatus(Long characterId, ImageGenerationStatus status) {
        characterRepository.updateImageGenerationStatus(characterId, status);
        log.debug("캐릭터 ID {} 상태 업데이트: {}", characterId, status);
    }

    /**
     * 캐릭터의 프로필 이미지 URL과 상태를 동시에 업데이트
     * 
     * @param characterId 캐릭터 ID
     * @param imageUrl 이미지 URL
     * @param status 상태
     */
    @Transactional
    protected void updateCharacterImageAndStatus(Long characterId, String imageUrl, ImageGenerationStatus status) {
        characterRepository.updateProfileImageAndStatus(characterId, imageUrl, status);
        log.info("캐릭터 ID {} 이미지 URL 및 상태 업데이트 완료 - Status: {}", characterId, status);
    }

    /**
     * 이미지 생성 실패 시 폴백 이미지로 설정
     * 
     * @param character 대상 캐릭터
     */
    @Transactional
    protected void saveFallbackImage(Character character) {
        try {
            String fallbackUrl = imageProperties.getFallbackUrl();
            updateCharacterImageAndStatus(character.getId(), fallbackUrl, ImageGenerationStatus.FAILED);
            log.info("캐릭터 '{}' - 폴백 이미지 설정 완료", character.getName());
        } catch (Exception e) {
            log.error("캐릭터 '{}' - 폴백 이미지 설정 실패", character.getName(), e);
        }
    }
}

