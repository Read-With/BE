package com.kw.readwith.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ShallowEtagHeaderFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * Spring의 자동 ETag 지원을 위한 ShallowEtagHeaderFilter 설정
     *
     * 동작 원리:
     * 1. 응답 본문의 MD5 해시값으로 ETag 자동 생성
     * 2. 클라이언트의 If-None-Match 헤더와 비교
     * 3. 동일하면 304 Not Modified 응답 (본문 없음)
     * 4. 다르면 200 OK 응답 (본문 포함)
     */
    @Bean
    public FilterRegistrationBean<ShallowEtagHeaderFilter> shallowEtagHeaderFilter() {
        FilterRegistrationBean<ShallowEtagHeaderFilter> filterRegistrationBean = 
            new FilterRegistrationBean<>(new ShallowEtagHeaderFilter());
        
        // Manifest API에만 ETag 적용
        filterRegistrationBean.addUrlPatterns("/api/books/*/manifest");
        
        // 필터 순서 설정 (낮을수록 먼저 실행)
        filterRegistrationBean.setOrder(1);
        
        return filterRegistrationBean;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**") // /api/ 로 시작하는 모든 경로에 대해
                .allowedOrigins("http://localhost:5173") // 프론트엔드 서버 주소 허용
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS") // 허용할 HTTP 메서드
                .allowedHeaders("*") // 모든 헤더 허용
                .allowCredentials(true)
                .maxAge(3600);
    }
}
