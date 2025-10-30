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
     * - 코어 스레드: 3개 (기본적으로 유지되는 스레드)
     * - 최대 스레드: 10개 (동시 처리 가능한 최대 이미지 수)
     * - 큐 용량: 50개 (대기 가능한 작업 수)
     */
    @Bean(name = "imageGenerationExecutor")
    public Executor imageGenerationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("ImageGen-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}

