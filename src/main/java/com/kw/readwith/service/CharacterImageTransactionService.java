package com.kw.readwith.service;

import com.kw.readwith.domain.enums.ImageGenerationStatus;
import com.kw.readwith.repository.CharacterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 캐릭터 이미지 생성 관련 트랜잭션 처리 서비스
 * 비동기 메서드에서 DB 업데이트를 위한 별도 트랜잭션 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CharacterImageTransactionService {

    private final CharacterRepository characterRepository;

    /**
     * 캐릭터의 이미지 생성 상태만 업데이트
     *
     * @param characterId 캐릭터 ID
     * @param status 변경할 상태
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateStatus(Long characterId, ImageGenerationStatus status) {
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
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateImageAndStatus(Long characterId, String imageUrl, ImageGenerationStatus status) {
        characterRepository.updateProfileImageAndStatus(characterId, imageUrl, status);
        log.info("캐릭터 ID {} 이미지 URL 및 상태 업데이트 완료 - Status: {}", characterId, status);
    }
}

