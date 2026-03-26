package com.kw.readwith.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.domain.Chapter;
import com.kw.readwith.dto.common.LocatorDTO;
import com.kw.readwith.service.normalization.LocatorResolutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LocatorSupport {

    private final ObjectMapper objectMapper;
    private final LocatorResolutionService locatorResolutionService;

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
        return locatorResolutionService.toTxtOffset(chapter, locator);
    }

    public LocatorDTO toLocator(Chapter chapter, int txtOffset) {
        return locatorResolutionService.toLocator(chapter, txtOffset);
    }

    public boolean hasLocatorMetadata(Chapter chapter) {
        return locatorResolutionService.hasLocatorMetadata(chapter);
    }

    public int resolveTotalCodePoints(Chapter chapter) {
        return locatorResolutionService.resolveTotalCodePoints(chapter);
    }
}
