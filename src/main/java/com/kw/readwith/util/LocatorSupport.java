package com.kw.readwith.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.domain.Chapter;
import com.kw.readwith.dto.common.LocatorDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class LocatorSupport {

    private final ObjectMapper objectMapper;

    public String writeLocator(LocatorDTO locator) {
        if (locator == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(locator);
        } catch (JsonProcessingException e) {
            throw new GeneralException(ErrorStatus._INTERNAL_SERVER_ERROR, "locator 직렬화에 실패했습니다.");
        }
    }

    public LocatorDTO readLocator(String locatorJson) {
        if (locatorJson == null || locatorJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(locatorJson, LocatorDTO.class);
        } catch (JsonProcessingException e) {
            throw new GeneralException(ErrorStatus._INTERNAL_SERVER_ERROR, "locator 역직렬화에 실패했습니다.");
        }
    }

    public int toTxtOffset(Chapter chapter, LocatorDTO locator) {
        if (locator == null) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "locator는 필수입니다.");
        }
        if (chapter.getIdx() != locator.getChapterIndex()) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "locator의 chapterIndex가 요청한 챕터와 일치하지 않습니다.");
        }

        List<Integer> paragraphStarts = readIntegerList(chapter.getParagraphStartsJson(), "paragraphStartsJson");
        List<Integer> paragraphLengths = readIntegerList(chapter.getParagraphLengthsJson(), "paragraphLengthsJson");

        if (paragraphStarts.size() != paragraphLengths.size()) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "챕터 locator 메타가 올바르지 않습니다.");
        }

        Integer blockIndex = locator.getBlockIndex();
        Integer offset = locator.getOffset();
        if (blockIndex == null || offset == null) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "locator의 blockIndex와 offset은 필수입니다.");
        }
        if (blockIndex < 0 || blockIndex >= paragraphStarts.size()) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "locator의 blockIndex 범위가 올바르지 않습니다.");
        }

        int blockLength = paragraphLengths.get(blockIndex);
        if (offset < 0 || offset > blockLength) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "locator의 offset 범위가 올바르지 않습니다.");
        }

        return paragraphStarts.get(blockIndex) + offset;
    }

    public LocatorDTO toLocator(Chapter chapter, int txtOffset) {
        List<Integer> paragraphStarts = readIntegerList(chapter.getParagraphStartsJson(), "paragraphStartsJson");
        List<Integer> paragraphLengths = readIntegerList(chapter.getParagraphLengthsJson(), "paragraphLengthsJson");

        if (paragraphStarts.size() != paragraphLengths.size() || paragraphStarts.isEmpty()) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "챕터 locator 메타가 올바르지 않습니다.");
        }
        if (txtOffset < 0 || txtOffset > resolveTotalCodePoints(chapter)) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "txtOffset 범위가 올바르지 않습니다.");
        }

        for (int i = 0; i < paragraphStarts.size(); i++) {
            int start = paragraphStarts.get(i);
            int endExclusive = start + paragraphLengths.get(i);
            if (txtOffset < endExclusive) {
                return LocatorDTO.builder()
                        .chapterIndex(chapter.getIdx())
                        .blockIndex(i)
                        .offset(txtOffset - start)
                        .build();
            }
        }

        int lastIndex = paragraphStarts.size() - 1;
        return LocatorDTO.builder()
                .chapterIndex(chapter.getIdx())
                .blockIndex(lastIndex)
                .offset(paragraphLengths.get(lastIndex))
                .build();
    }

    public boolean hasLocatorMetadata(Chapter chapter) {
        return chapter.getParagraphStartsJson() != null
                && !chapter.getParagraphStartsJson().isBlank()
                && chapter.getParagraphLengthsJson() != null
                && !chapter.getParagraphLengthsJson().isBlank();
    }

    public int resolveTotalCodePoints(Chapter chapter) {
        if (chapter.getTotalCodePoints() != null) {
            return chapter.getTotalCodePoints();
        }

        List<Integer> paragraphStarts = readIntegerList(chapter.getParagraphStartsJson(), "paragraphStartsJson");
        List<Integer> paragraphLengths = readIntegerList(chapter.getParagraphLengthsJson(), "paragraphLengthsJson");
        if (paragraphStarts.size() != paragraphLengths.size() || paragraphStarts.isEmpty()) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "챕터 totalCodePoints를 계산할 수 없습니다.");
        }

        int lastIndex = paragraphStarts.size() - 1;
        return paragraphStarts.get(lastIndex) + paragraphLengths.get(lastIndex);
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
}
