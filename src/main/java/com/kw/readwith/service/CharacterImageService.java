package com.kw.readwith.service;

import com.kw.readwith.aws.s3.AmazonS3Manager;
import com.kw.readwith.config.CharacterImageProperties;
import com.kw.readwith.domain.Character;
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
                // 이미 이미지가 있는 경우 스킵
                if (character.getProfileImage() != null && !character.getProfileImage().isEmpty()) {
                    log.info("캐릭터 '{}' - 이미지가 이미 존재하여 스킵", character.getName());
                    continue;
                }

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
            String imageUrl = response.getResult().getOutput().getUrl();
            log.info("캐릭터 '{}' DALL-E 이미지 생성 완료: {}", character.getName(), imageUrl);

            // 4. S3에 업로드 (base64 또는 URL 다운로드 후 업로드)
            // DALL-E 3는 URL을 반환하므로, 필요시 다운로드 후 S3에 업로드
            // 현재는 DALL-E가 제공하는 URL을 직접 저장
            String s3Url = imageUrl; // 추후 S3 업로드 로직 추가 가능
            
            // 5. Character 엔티티 업데이트
            updateCharacterImage(character, s3Url);
            
            log.info("캐릭터 '{}' 이미지 저장 완료", character.getName());

        } catch (Exception e) {
            log.error("캐릭터 '{}' 이미지 생성 중 오류 발생", character.getName(), e);
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
     * 캐릭터 엔티티의 profileImage 필드 업데이트
     * 
     * @param character 업데이트할 캐릭터
     * @param imageUrl 이미지 URL
     */
    @Transactional
    protected void updateCharacterImage(Character character, String imageUrl) {
        character = characterRepository.findById(character.getId())
            .orElseThrow(() -> new IllegalArgumentException("Character not found: " + character.getId()));
        
        // Character 엔티티에는 setter가 없으므로 리플렉션 또는 별도 업데이트 메서드 필요
        // 임시로 직접 쿼리 사용
        characterRepository.updateProfileImage(character.getId(), imageUrl);
        
        log.info("캐릭터 ID {} 이미지 URL 업데이트 완료", character.getId());
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
            updateCharacterImage(character, fallbackUrl);
            log.info("캐릭터 '{}' - 폴백 이미지 설정 완료", character.getName());
        } catch (Exception e) {
            log.error("캐릭터 '{}' - 폴백 이미지 설정 실패", character.getName(), e);
        }
    }
}

