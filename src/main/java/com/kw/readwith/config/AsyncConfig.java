package com.kw.readwith.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 비동기 작업 처리를 위한 설정
 * 캐릭터 이미지 생성과 같은 시간이 오래 걸리는 작업을 별도 스레드에서 처리
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 이미지 생성 전용 ThreadPool Executor
     * AWS 프리티어 환경을 고려한 보수적인 설정
     * - 코어 스레드: 1개 (순차 처리로 서버 부하 최소화)
     * - 최대 스레드: 2개 (긴급 시에만 추가 스레드 사용)
     * - 큐 용량: 100개 (많은 캐릭터도 대기 가능)
     * 
     * 100명 캐릭터 처리 시: 약 40-50분 소요 (느리지만 안정적)
     */
    @Bean(name = "imageGenerationExecutor")
    public Executor imageGenerationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1); // 기본 1개 스레드 (순차 처리)
        executor.setMaxPoolSize(2); // 최대 2개 (부하 방지)
        executor.setQueueCapacity(100); // 넉넉한 큐 용량
        executor.setThreadNamePrefix("ImageGen-");
        executor.setKeepAliveSeconds(60); // 유휴 스레드 1분 후 제거
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(180); // 종료 시 최대 3분 대기
        executor.initialize();
        return executor;
    }
}

