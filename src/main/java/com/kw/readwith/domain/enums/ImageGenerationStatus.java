package com.kw.readwith.domain.enums;

/**
 * 캐릭터 이미지 생성 상태를 나타내는 Enum
 */
public enum ImageGenerationStatus {
    /**
     * 이미지 생성 대기 중
     */
    PENDING,
    
    /**
     * 이미지 생성 중 (DALL-E API 호출 중)
     */
    GENERATING,
    
    /**
     * 이미지 생성 및 S3 업로드 완료
     */
    COMPLETED,
    
    /**
     * 이미지 생성 실패 (폴백 이미지 사용)
     */
    FAILED
}

