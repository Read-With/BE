package com.kw.readwith.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ShallowEtagHeaderFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final V2ApiContractInterceptor v2ApiContractInterceptor;

    public WebConfig(V2ApiContractInterceptor v2ApiContractInterceptor) {
        this.v2ApiContractInterceptor = v2ApiContractInterceptor;
    }

    /**
     * Spring???먮룞 ETag 吏?먯쓣 ?꾪븳 ShallowEtagHeaderFilter ?ㅼ젙
     *
     * ?숈옉 ?먮━:
     * 1. ?묐떟 蹂몃Ц??MD5 ?댁떆媛믪쑝濡?ETag ?먮룞 ?앹꽦
     * 2. ?대씪?댁뼵?몄쓽 If-None-Match ?ㅻ뜑? 鍮꾧탳
     * 3. ?숈씪?섎㈃ 304 Not Modified ?묐떟 (蹂몃Ц ?놁쓬)
     * 4. ?ㅻⅤ硫?200 OK ?묐떟 (蹂몃Ц ?ы븿)
     */
    @Bean
    public FilterRegistrationBean<ShallowEtagHeaderFilter> shallowEtagHeaderFilter() {
        FilterRegistrationBean<ShallowEtagHeaderFilter> filterRegistrationBean =
                new FilterRegistrationBean<>(new ShallowEtagHeaderFilter());

        filterRegistrationBean.addUrlPatterns("/api/books/*/manifest");
        filterRegistrationBean.addUrlPatterns("/api/v2/books/*/manifest");
        filterRegistrationBean.setOrder(1);

        return filterRegistrationBean;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(v2ApiContractInterceptor)
                .addPathPatterns("/api/**", "/api/v2/**");
    }
}
