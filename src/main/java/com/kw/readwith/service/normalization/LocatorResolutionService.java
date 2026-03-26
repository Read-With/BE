package com.kw.readwith.service.normalization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.domain.Chapter;
import com.kw.readwith.dto.common.LocatorDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class LocatorResolutionService {

    private final ObjectMapper objectMapper;
    private final NormalizedArtifactStorageService normalizedArtifactStorageService;

    private final Map<Long, ChapterLocatorProjection> projectionCache = new ConcurrentHashMap<>();
    private final Map<String, ChapterLocatorProjection> metaCache = new ConcurrentHashMap<>();

    public int toTxtOffset(Chapter chapter, LocatorDTO locator) {
        if (locator == null) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "locator는 필수입니다.");
        }
        if (chapter.getIdx() != locator.getChapterIndex()) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "locator chapterIndex가 요청한 챕터와 일치하지 않습니다.");
        }

        ChapterLocatorProjection projection = resolveProjection(chapter);
        Integer blockIndex = locator.getBlockIndex();
        Integer offset = locator.getOffset();
        if (blockIndex == null || offset == null) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "locator blockIndex와 offset은 필수입니다.");
        }
        if (blockIndex < 0 || blockIndex >= projection.paragraphStarts().size()) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "locator blockIndex 범위가 올바르지 않습니다.");
        }

        int blockLength = projection.paragraphLengths().get(blockIndex);
        if (offset < 0 || offset > blockLength) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "locator offset 범위가 올바르지 않습니다.");
        }

        return projection.paragraphStarts().get(blockIndex) + offset;
    }

    public LocatorDTO toLocator(Chapter chapter, int txtOffset) {
        ChapterLocatorProjection projection = resolveProjection(chapter);
        if (txtOffset < 0 || txtOffset > projection.totalCodePoints()) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "txtOffset 범위가 올바르지 않습니다.");
        }

        for (int i = 0; i < projection.paragraphStarts().size(); i++) {
            int start = projection.paragraphStarts().get(i);
            int endExclusive = start + projection.paragraphLengths().get(i);
            if (txtOffset < endExclusive) {
                return LocatorDTO.builder()
                        .chapterIndex(chapter.getIdx())
                        .blockIndex(i)
                        .offset(txtOffset - start)
                        .build();
            }
        }

        int lastIndex = projection.paragraphStarts().size() - 1;
        return LocatorDTO.builder()
                .chapterIndex(chapter.getIdx())
                .blockIndex(lastIndex)
                .offset(projection.paragraphLengths().get(lastIndex))
                .build();
    }

    public boolean hasLocatorMetadata(Chapter chapter) {
        return chapter.getParagraphStartsJson() != null
                && !chapter.getParagraphStartsJson().isBlank()
                && chapter.getParagraphLengthsJson() != null
                && !chapter.getParagraphLengthsJson().isBlank();
    }

    public int resolveTotalCodePoints(Chapter chapter) {
        return resolveProjection(chapter).totalCodePoints();
    }

    public String writeIntegerList(List<Integer> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            throw new GeneralException(ErrorStatus._INTERNAL_SERVER_ERROR, "locator 메타 직렬화에 실패했습니다.");
        }
    }

    public void evictAll() {
        projectionCache.clear();
        metaCache.clear();
    }

    private ChapterLocatorProjection resolveProjection(Chapter chapter) {
        if (hasLocatorMetadata(chapter)) {
            if (chapter.getId() == null) {
                return parseProjection(chapter);
            }
            return projectionCache.computeIfAbsent(chapter.getId(), ignored -> parseProjection(chapter));
        }

        ChapterLocatorProjection metaProjection = resolveProjectionFromMeta(chapter);
        if (metaProjection != null) {
            return metaProjection;
        }

        throw new GeneralException(ErrorStatus._BAD_REQUEST, "챕터 locator 메타가 준비되지 않았습니다.");
    }

    private ChapterLocatorProjection parseProjection(Chapter chapter) {
        List<Integer> paragraphStarts = readIntegerList(chapter.getParagraphStartsJson(), "paragraphStartsJson");
        List<Integer> paragraphLengths = readIntegerList(chapter.getParagraphLengthsJson(), "paragraphLengthsJson");
        if (paragraphStarts.size() != paragraphLengths.size() || paragraphStarts.isEmpty()) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "챕터 locator 메타가 올바르지 않습니다.");
        }

        int totalCodePoints = chapter.getTotalCodePoints() != null
                ? chapter.getTotalCodePoints()
                : paragraphStarts.get(paragraphStarts.size() - 1) + paragraphLengths.get(paragraphLengths.size() - 1);

        return new ChapterLocatorProjection(paragraphStarts, paragraphLengths, totalCodePoints);
    }

    private List<Integer> readIntegerList(String json, String fieldName) {
        if (json == null || json.isBlank()) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, fieldName + "가 비어 있습니다.");
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Integer>>() {});
        } catch (JsonProcessingException e) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, fieldName + " 파싱에 실패했습니다.");
        }
    }

    private ChapterLocatorProjection resolveProjectionFromMeta(Chapter chapter) {
        if (chapter.getBook() == null || chapter.getBook().getNormalizedArtifactPath() == null
                || chapter.getBook().getNormalizedArtifactPath().isBlank()) {
            return null;
        }

        String cacheKey = chapter.getBook().getNormalizedArtifactPath() + ":" + chapter.getIdx();
        return metaCache.computeIfAbsent(cacheKey, ignored -> loadProjectionFromMeta(
                chapter.getBook().getNormalizedArtifactPath(),
                chapter.getIdx()
        ));
    }

    private ChapterLocatorProjection loadProjectionFromMeta(String artifactRoot, int chapterIndex) {
        try {
            byte[] metaBytes = normalizedArtifactStorageService.loadPrivateObject(artifactRoot + "/meta.json");
            JsonNode metaRoot = objectMapper.readTree(metaBytes);
            for (JsonNode chapterNode : metaRoot.path("chapters")) {
                if (chapterNode.path("chapterIndex").asInt() != chapterIndex) {
                    continue;
                }
                List<Integer> paragraphStarts = objectMapper.convertValue(
                        chapterNode.path("paragraphStarts"),
                        new TypeReference<List<Integer>>() {}
                );
                List<Integer> paragraphLengths = objectMapper.convertValue(
                        chapterNode.path("paragraphLengths"),
                        new TypeReference<List<Integer>>() {}
                );
                return new ChapterLocatorProjection(
                        paragraphStarts,
                        paragraphLengths,
                        chapterNode.path("totalCodePoints").asInt()
                );
            }
            return null;
        } catch (Exception e) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "meta.json 기반 locator 메타를 불러오지 못했습니다.");
        }
    }

    private record ChapterLocatorProjection(
            List<Integer> paragraphStarts,
            List<Integer> paragraphLengths,
            int totalCodePoints
    ) {
    }
}
