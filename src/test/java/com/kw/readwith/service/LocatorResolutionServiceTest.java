package com.kw.readwith.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.Chapter;
import com.kw.readwith.dto.common.LocatorDTO;
import com.kw.readwith.service.normalization.LocatorResolutionService;
import com.kw.readwith.service.normalization.NormalizedArtifactStorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocatorResolutionServiceTest {

    private final NormalizedArtifactStorageService normalizedArtifactStorageService = mock(NormalizedArtifactStorageService.class);
    private final LocatorResolutionService locatorResolutionService =
            new LocatorResolutionService(new ObjectMapper(), normalizedArtifactStorageService);

    @Test
    @DisplayName("locator와 txtOffset이 챕터 projection 기준으로 round-trip 된다")
    void locatorRoundTrip() {
        Chapter chapter = Chapter.builder()
                .id(1L)
                .idx(1)
                .paragraphStartsJson("[0,5,9]")
                .paragraphLengthsJson("[5,4,3]")
                .totalCodePoints(12)
                .pageStart(0)
                .pageEnd(0)
                .startPos(0)
                .endPos(11)
                .povSummariesCached(false)
                .build();

        LocatorDTO locator = LocatorDTO.builder()
                .chapterIndex(1)
                .blockIndex(1)
                .offset(2)
                .build();

        int txtOffset = locatorResolutionService.toTxtOffset(chapter, locator);
        LocatorDTO resolved = locatorResolutionService.toLocator(chapter, txtOffset);

        assertThat(txtOffset).isEqualTo(7);
        assertThat(resolved.getChapterIndex()).isEqualTo(1);
        assertThat(resolved.getBlockIndex()).isEqualTo(1);
        assertThat(resolved.getOffset()).isEqualTo(2);
    }

    @Test
    @DisplayName("챕터 끝 txtOffset은 마지막 block 끝 locator로 변환된다")
    void endOffsetResolvesToLastBlockEnd() {
        Chapter chapter = Chapter.builder()
                .id(2L)
                .idx(3)
                .paragraphStartsJson("[0,3]")
                .paragraphLengthsJson("[3,2]")
                .totalCodePoints(5)
                .pageStart(0)
                .pageEnd(0)
                .startPos(0)
                .endPos(4)
                .povSummariesCached(false)
                .build();

        LocatorDTO resolved = locatorResolutionService.toLocator(chapter, 5);

        assertThat(resolved.getChapterIndex()).isEqualTo(3);
        assertThat(resolved.getBlockIndex()).isEqualTo(1);
        assertThat(resolved.getOffset()).isEqualTo(2);
    }

    @Test
    @DisplayName("챕터 projection이 비어 있으면 meta.json 캐시에서 locator 메타를 읽는다")
    void fallsBackToMetaJsonWhenProjectionMissing() {
        Book book = Book.builder()
                .id(10L)
                .normalizedArtifactPath("books/10/normalizations/run-1")
                .build();
        Chapter chapter = Chapter.builder()
                .id(3L)
                .book(book)
                .idx(2)
                .pageStart(0)
                .pageEnd(0)
                .startPos(0)
                .endPos(0)
                .povSummariesCached(false)
                .build();

        when(normalizedArtifactStorageService.loadPrivateObject("books/10/normalizations/run-1/meta.json"))
                .thenReturn("""
                        {
                          "chapters": [
                            {
                              "chapterIndex": 2,
                              "paragraphStarts": [0, 4],
                              "paragraphLengths": [4, 3],
                              "totalCodePoints": 7
                            }
                          ]
                        }
                        """.getBytes());

        LocatorDTO locator = locatorResolutionService.toLocator(chapter, 5);

        assertThat(locator.getChapterIndex()).isEqualTo(2);
        assertThat(locator.getBlockIndex()).isEqualTo(1);
        assertThat(locator.getOffset()).isEqualTo(1);
    }
}
