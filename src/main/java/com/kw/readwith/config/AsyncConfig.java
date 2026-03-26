package com.kw.readwith.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 비동기 작업 설정
 * t2.micro 환경에 최적화된 스레드 풀 설정
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    /**
     * 캐릭터 이미지 생성용 스레드 풀
     * t2.micro(1GB RAM, 1 vCPU) 환경에 맞춰 제한적으로 설정
     */
    @Bean(name = "imageGenerationExecutor")
    public Executor imageGenerationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 코어 스레드: 동시에 2개의 이미지만 생성
        executor.setCorePoolSize(2);
        
        // 최대 스레드: 부하가 높을 때 최대 3개까지 허용
        executor.setMaxPoolSize(3);
        
        // 대기 큐: 50개까지 대기 가능
        executor.setQueueCapacity(50);
        
        // 스레드 이름 prefix (로그 추적 용이)
        executor.setThreadNamePrefix("image-gen-");
        
        // 큐가 가득 찰 경우 호출한 스레드에서 직접 실행
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        // 스레드 풀 초기화
        executor.initialize();
        
        return executor;
    }

    @Bean(name = "normalizationJobExecutor")
    public Executor normalizationJobExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("normalization-job-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
    
    @Override
    public Executor getAsyncExecutor() {
        return imageGenerationExecutor();
    }
}
