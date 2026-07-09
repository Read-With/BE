package com.kw.readwith.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SwaggerConfigTest {

    @Test
    void hideLegacyApiAliasesRemovesOnlyPathsWithV2Counterparts() {
        OpenAPI openAPI = new OpenAPI().paths(new Paths()
                .addPathItem("/api/books", new PathItem())
                .addPathItem("/api/v2/books", new PathItem())
                .addPathItem("/api/admin/books/{bookId}/relationship-delta-jobs", new PathItem())
                .addPathItem("/api/v2/admin/books/{bookId}/relationship-delta-jobs", new PathItem())
                .addPathItem("/api/auth/login", new PathItem()));

        new SwaggerConfig().hideLegacyApiAliases().customise(openAPI);

        assertThat(openAPI.getPaths()).doesNotContainKey("/api/books");
        assertThat(openAPI.getPaths()).doesNotContainKey("/api/admin/books/{bookId}/relationship-delta-jobs");
        assertThat(openAPI.getPaths()).containsKey("/api/v2/books");
        assertThat(openAPI.getPaths()).containsKey("/api/v2/admin/books/{bookId}/relationship-delta-jobs");
        assertThat(openAPI.getPaths()).containsKey("/api/auth/login");
    }
}
