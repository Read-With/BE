package com.kw.readwith.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "cloud.aws.credentials.accessKey=test",
        "cloud.aws.credentials.secretKey=test",
        "cloud.aws.region.static=ap-northeast-2",
        "cloud.aws.s3.bucket=test-bucket",
        "cloud.aws.path.original=original",
        "cloud.aws.path.metadata=metadata",
        "spring.jpa.properties.hibernate.hbm2ddl.auto=create-drop",
        "spring.security.oauth2.client.registration.google.client-id=test",
        "spring.security.oauth2.client.registration.google.client-secret=test",
        "spring.ai.openai.api-key=test"
})
class ApiRouteAliasMappingTest {

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void exposesV2AliasesForBookFavoriteAndPovSummaryApis() {
        Set<String> paths = handlerMapping.getHandlerMethods().keySet().stream()
                .flatMap(info -> info.getPatternValues().stream())
                .collect(Collectors.toSet());

        assertThat(paths).contains("/api/books");
        assertThat(paths).contains("/api/v2/books");
        assertThat(paths).contains("/api/books/{bookId}");
        assertThat(paths).contains("/api/v2/books/{bookId}");
        assertThat(paths).contains("/api/books/{bookId}/chapters/{chapterIdx}/pov-summaries");
        assertThat(paths).contains("/api/v2/books/{bookId}/chapters/{chapterIdx}/pov-summaries");
        assertThat(paths).contains("/api/favorites");
        assertThat(paths).contains("/api/v2/favorites");
        assertThat(paths).contains("/api/favorites/{bookId}");
        assertThat(paths).contains("/api/v2/favorites/{bookId}");
    }
}
