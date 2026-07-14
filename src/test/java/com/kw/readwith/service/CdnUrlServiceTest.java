package com.kw.readwith.service;

import com.kw.readwith.config.AmazonConfig;
import com.kw.readwith.config.ArtifactStorageProperties;
import com.kw.readwith.config.CharacterImageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class CdnUrlServiceTest {

    @Mock
    private ArtifactStorageProperties artifactStorageProperties;
    @Mock
    private AmazonConfig amazonConfig;
    @Mock
    private CharacterImageProperties characterImageProperties;

    private CdnUrlService cdnUrlService;

    @BeforeEach
    void setUp() {
        lenient().when(artifactStorageProperties.getPublicPrefix()).thenReturn("public");
        lenient().when(artifactStorageProperties.getCloudFrontBaseUrl()).thenReturn("https://cdn.readwith.store/");
        lenient().when(characterImageProperties.getS3Path()).thenReturn("character-images");
        lenient().when(amazonConfig.getBucket()).thenReturn("readwith-s3-bucket");
        lenient().when(amazonConfig.getRegion()).thenReturn("ap-northeast-2");

        cdnUrlService = new CdnUrlService(artifactStorageProperties, amazonConfig, characterImageProperties);
    }

    @Test
    void toPublicUrl_convertsPublicObjectKey() {
        String result = cdnUrlService.toPublicUrl("public/books/20/covers/source-v1/cover.jpg");

        assertThat(result).isEqualTo("https://cdn.readwith.store/public/books/20/covers/source-v1/cover.jpg");
    }

    @Test
    void toPublicUrl_convertsVirtualHostedS3Url() {
        String result = cdnUrlService.toPublicUrl(
                "https://readwith-s3-bucket.s3.ap-northeast-2.amazonaws.com/character-images/1/10.png"
        );

        assertThat(result).isEqualTo("https://cdn.readwith.store/character-images/1/10.png");
    }

    @Test
    void toPublicUrl_keepsExternalUrl() {
        String result = cdnUrlService.toPublicUrl("https://example.com/image.png");

        assertThat(result).isEqualTo("https://example.com/image.png");
    }
}
