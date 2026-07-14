package com.kw.readwith.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 비동기 작업 설정
 * t2.micro 환경에 최적화된 스레드 풀 설정
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig implements AsyncConfigurer {

    /**
     * 캐릭터 이미지 생성용 스레드 풀
     * t2.micro(1GB RAM, 1 vCPU) 환경에 맞춰 제한적으로 설정
     */
    @Bean(name = "imageGenerationExecutor")
    public Executor imageGenerationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // Render Free 512MB 환경에서는 이미지 생성을 한 번에 하나만 실행한다.
        executor.setCorePoolSize(1);
        
        executor.setMaxPoolSize(1);
        
        executor.setQueueCapacity(10);
        
        // 스레드 이름 prefix (로그 추적 용이)
        executor.setThreadNamePrefix("image-gen-");
        executor.setAllowCoreThreadTimeOut(true);
        executor.setKeepAliveSeconds(60);
        
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
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(5);
        executor.setThreadNamePrefix("normalization-job-");
        executor.setAllowCoreThreadTimeOut(true);
        executor.setKeepAliveSeconds(60);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
    
    @Override
    public Executor getAsyncExecutor() {
        return imageGenerationExecutor();
    }
}
