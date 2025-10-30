package com.kw.readwith.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 캐릭터 이미지 생성 관련 설정을 관리하는 Properties 클래스
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "character-image")
public class CharacterImageProperties {

    /**
     * 이미지 생성 실패 시 사용할 기본 이미지 URL
     */
    private String fallbackUrl;

    /**
     * DALL-E 프롬프트에 공통적으로 적용할 아트 스타일
     */
    private String commonStyle;

    /**
     * S3에 이미지를 저장할 경로 (버킷 내 디렉토리)
     */
    private String s3Path;

    /**
     * OpenAI API Rate Limit 방지를 위한 요청 간 대기 시간 (밀리초)
     * 기본값: 2000ms (2초)
     */
    private long delayBetweenRequestsMs = 2000;

    /**
     * 한 번에 처리할 배치 크기
     * 기본값: 10명
     */
    private int batchSize = 10;
}

