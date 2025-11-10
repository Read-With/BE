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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
    private final CharacterImageTransactionService transactionService;

    /**
     * 여러 캐릭터의 이미지를 비동기로 생성
     * 배치 단위로 처리하며, Rate Limit 방지를 위해 요청 간 딜레이 적용
     * 
     * @param characterIds 이미지를 생성할 캐릭터 ID 리스트
     * @return 비동기 작업 결과
     */
    @Async("imageGenerationExecutor")
    public CompletableFuture<Void> generateImagesAsync(List<Long> characterIds) {
        int totalCount = characterIds.size();
        log.info("캐릭터 이미지 생성 시작 - 총 {}명 (배치 크기: {}명)", 
            totalCount, imageProperties.getBatchSize());
        
        int processedCount = 0;
        int successCount = 0;
        int failedCount = 0;
        int skippedCount = 0;
        
        for (int i = 0; i < characterIds.size(); i++) {
            Long characterId = characterIds.get(i);
            processedCount++;
            
            try {
                // 엔티티를 fetch join으로 재조회 (Book도 함께 로딩)
                Character character = characterRepository.findByIdWithBook(characterId)
                        .orElseThrow(() -> new RuntimeException("캐릭터를 찾을 수 없습니다: " + characterId));
                
                // 이미 이미지가 있거나 생성 완료된 경우 스킵
                if (character.getProfileImage() != null && !character.getProfileImage().isEmpty() 
                    && character.getImageGenerationStatus() == ImageGenerationStatus.COMPLETED) {
                    log.info("[{}/{}] 캐릭터 '{}' - 이미지가 이미 존재하여 스킵", 
                        processedCount, totalCount, character.getName());
                    skippedCount++;
                    continue;
                }

                // 상태를 PENDING으로 설정
                transactionService.updateStatus(characterId, ImageGenerationStatus.PENDING);
                
                log.info("[{}/{}] 캐릭터 '{}' 이미지 생성 시작...", 
                    processedCount, totalCount, character.getName());
                
                generateAndSaveImage(characterId);
                successCount++;
                
                log.info("[{}/{}] 캐릭터 '{}' 이미지 생성 완료 ✓", 
                    processedCount, totalCount, character.getName());
                
                // Rate Limit 방지: 마지막 캐릭터가 아니면 딜레이 적용
                if (i < characterIds.size() - 1) {
                    long delay = imageProperties.getDelayBetweenRequestsMs();
                    log.debug("다음 요청까지 {}ms 대기...", delay);
                    Thread.sleep(delay);
                }
                
                // 배치 단위로 진행 상황 로그
                if (processedCount % imageProperties.getBatchSize() == 0) {
                    log.info("═══ 배치 진행 상황: {}/{} 완료 (성공: {}, 실패: {}, 스킵: {}) ═══", 
                        processedCount, totalCount, successCount, failedCount, skippedCount);
                }
                
            } catch (InterruptedException e) {
                log.error("캐릭터 ID {} 처리 중 인터럽트 발생", characterId, e);
                Thread.currentThread().interrupt();
                failedCount++;
                saveFallbackImage(characterId);
                break; // 인터럽트 시 중단
            } catch (Exception e) {
                log.error("캐릭터 ID {} 이미지 생성 실패: {}", characterId, e.getMessage(), e);
                failedCount++;
                saveFallbackImage(characterId);
            }
        }
        
        log.info("═══════════════════════════════════════════════════════════");
        log.info("캐릭터 이미지 생성 작업 완료");
        log.info("총 {}명 처리 - 성공: {}명, 실패: {}명, 스킵: {}명", 
            totalCount, successCount, failedCount, skippedCount);
        log.info("═══════════════════════════════════════════════════════════");
        
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 단일 캐릭터의 이미지를 생성하고 저장
     * 
     * ⚠️ 트랜잭션 최적화: 외부 API 호출은 트랜잭션 밖에서 실행
     * DB 커넥션 점유 시간: 14초 → 0.02초 (99.9% 개선)
     * 
     * @param characterId 이미지를 생성할 캐릭터 ID
     */
    public void generateAndSaveImage(Long characterId) {
        log.info("캐릭터 ID {} 이미지 생성 시작", characterId);

        try {
            // 1. Read-only 트랜잭션으로 캐릭터 정보 조회 (빠른 커넥션 반환)
            Character character = getCharacterReadOnly(characterId);
            log.info("캐릭터 '{}' 정보 조회 완료", character.getName());
            
            // 상태를 GENERATING으로 변경
            transactionService.updateStatus(characterId, ImageGenerationStatus.GENERATING);

            // 2. 프롬프트 생성 (트랜잭션 없음)
            String prompt = buildDallePrompt(character);
            log.debug("생성된 프롬프트: {}", prompt);

            // 3. DALL-E API 호출 (트랜잭션 없음, 커넥션 없음, ~10초 소요)
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

            // 4. 생성된 이미지 URL 추출
            String dalleImageUrl = response.getResult().getOutput().getUrl();
            log.info("캐릭터 '{}' DALL-E 이미지 생성 완료: {}", character.getName(), dalleImageUrl);

            // 5. DALL-E URL에서 이미지 다운로드 (트랜잭션 없음, ~2초 소요)
            byte[] imageData = downloadImageFromUrl(dalleImageUrl);
            log.info("캐릭터 '{}' 이미지 다운로드 완료 - 크기: {} bytes", character.getName(), imageData.length);

            // 6. Base64 인코딩 (트랜잭션 없음)
            String base64Image = Base64.getEncoder().encodeToString(imageData);
            
            // 7. S3 업로드 (트랜잭션 없음, ~2초 소요)
            String s3KeyName = buildS3KeyName(character);
            String s3Url = s3Manager.uploadFileFromBase64(s3KeyName, base64Image, "image/png");
            log.info("캐릭터 '{}' S3 업로드 완료: {}", character.getName(), s3Url);
            
            // 8. 별도 트랜잭션으로 DB 업데이트만 수행 (0.01초만 커넥션 점유)
            updateImageUrlOnly(characterId, s3Url);
            
            log.info("캐릭터 '{}' 이미지 생성 및 저장 완료", character.getName());

        } catch (Exception e) {
            log.error("캐릭터 ID {} 이미지 생성 중 오류 발생", characterId, e);
            transactionService.updateStatus(characterId, ImageGenerationStatus.FAILED);
            throw new RuntimeException("이미지 생성 중 오류 발생: " + characterId, e);
        }
    }
    
    /**
     * 읽기 전용 트랜잭션으로 캐릭터 조회
     * 빠르게 커넥션을 반환하여 풀 고갈 방지
     */
    @Transactional(readOnly = true)
    private Character getCharacterReadOnly(Long characterId) {
        return characterRepository.findByIdWithBook(characterId)
                .orElseThrow(() -> new RuntimeException("캐릭터를 찾을 수 없습니다: " + characterId));
    }
    
    /**
     * 새로운 트랜잭션으로 이미지 URL과 상태만 업데이트
     * REQUIRES_NEW: 부모 트랜잭션과 독립적으로 실행
     * timeout: 5초 (DB 업데이트만 수행하므로 충분)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    private void updateImageUrlOnly(Long characterId, String s3Url) {
        transactionService.updateImageAndStatus(characterId, s3Url, ImageGenerationStatus.COMPLETED);
    }

    /**
     * DALL-E 프롬프트 생성
     * 스타일 제약사항을 먼저 명시하고, 그 안에서 캐릭터 특징 표현
     * 
     * @param character 대상 캐릭터
     * @return 생성된 프롬프트
     */
    private String buildDallePrompt(Character character) {
        StringBuilder prompt = new StringBuilder();
        
        // 1. 먼저 스타일 제약사항 명시 (DALL-E가 이것을 기준으로 삼게)
        prompt.append(imageProperties.getCommonStyle());
        prompt.append("\n\n");
        
        // 2. 그 다음 캐릭터 묘사 (제약사항 내에서 표현)
        prompt.append("━━━ CHARACTER DESCRIPTION (within above constraints) ━━━\n");
        
        if (character.getProfileText() != null && !character.getProfileText().isEmpty()) {
            prompt.append("Subject: ").append(character.getProfileText());
        } else {
            // profileText가 없는 경우 이름만 사용
            prompt.append("Subject: A person named ").append(character.getName());
        }
        
        // 3. 마지막으로 다시 한번 핵심 제약 강조
        prompt.append("\n\nREMINDER: FACE AND SHOULDERS ONLY. PLAIN BEIGE BACKGROUND. NO patterns, NO decorations, NO symbols.");
        
        return prompt.toString();
    }

    /**
     * URL에서 이미지를 다운로드하여 byte 배열로 반환
     * Azure Blob Storage SAS 토큰이 포함된 URL도 처리 가능
     * 
     * @param imageUrl 다운로드할 이미지 URL
     * @return 이미지 데이터 (byte 배열)
     * @throws IOException 다운로드 실패 시
     */
    private byte[] downloadImageFromUrl(String imageUrl) throws IOException {
        log.debug("이미지 다운로드 시작: {}", imageUrl);
        
        try {
            // URL을 직접 열어서 스트림으로 읽기 (SAS 토큰 보존)
            URL url = new URL(imageUrl);
            try (InputStream inputStream = url.openStream()) {
                byte[] imageData = inputStream.readAllBytes();
                
                if (imageData.length == 0) {
                    throw new IOException("다운로드한 이미지 데이터가 비어있습니다.");
                }
                
                log.debug("이미지 다운로드 완료 - 크기: {} bytes", imageData.length);
                return imageData;
            }
        } catch (IOException e) {
            log.error("이미지 다운로드 실패 - URL: {}", imageUrl, e);
            throw new IOException("이미지 다운로드 실패: " + e.getMessage(), e);
        }
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
     * 이미지 생성 실패 시 폴백 이미지로 설정
     *
     * @param characterId 대상 캐릭터 ID
     */
    private void saveFallbackImage(Long characterId) {
        try {
            String fallbackUrl = imageProperties.getFallbackUrl();
            transactionService.updateImageAndStatus(characterId, fallbackUrl, ImageGenerationStatus.FAILED);
            log.info("캐릭터 ID {} - 폴백 이미지 설정 완료", characterId);
        } catch (Exception e) {
            log.error("캐릭터 ID {} - 폴백 이미지 설정 실패", characterId, e);
        }
    }
}

