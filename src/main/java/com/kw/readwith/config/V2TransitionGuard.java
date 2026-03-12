package com.kw.readwith.config;

import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.Chapter;
import com.kw.readwith.domain.Event;
import com.kw.readwith.util.LocatorSupport;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
@RequiredArgsConstructor
public class V2TransitionGuard {

    private final ApiContractProperties apiContractProperties;
    private final EpubNormalizationProperties epubNormalizationProperties;
    private final LocatorProperties locatorProperties;
    private final LocatorSupport locatorSupport;

    public void ensureV2ManifestEnabled(HttpServletRequest request) {
        if (!isV2Request(request) || epubNormalizationProperties.isEnabled()) {
            return;
        }

        throw new GeneralException(
                ErrorStatus._FORBIDDEN,
                "epub.normalization.enabled=false 상태에서는 /api/v2/books/{bookId}/manifest 경로를 사용할 수 없습니다."
        );
    }

    public void ensureLocatorWritesEnabled(String operationName) {
        if (!locatorProperties.getResolvedMode().isOff()) {
            return;
        }

        throw new GeneralException(
                ErrorStatus._FORBIDDEN,
                "locator.mode=off 상태에서는 " + operationName + " 기능을 사용할 수 없습니다."
        );
    }

    public void ensureLocatorMetadataReady(Book book, Chapter chapter, String operationName) {
        if (!locatorProperties.getResolvedMode().isStrict()) {
            return;
        }

        ensureBookLocatorVersion(book, operationName);
        if (!locatorSupport.hasLocatorMetadata(chapter) || chapter.getTotalCodePoints() == null) {
            throw new GeneralException(
                    ErrorStatus._FORBIDDEN,
                    "locator.mode=strict 상태에서는 " + operationName + " 전에 chapter locator 메타가 모두 준비되어 있어야 합니다."
            );
        }
    }

    public void ensureManifestReady(Book book, List<Chapter> chapters, List<Event> events) {
        if (!apiContractProperties.getResolvedMode().isStrict()) {
            return;
        }

        ensureBookLocatorVersion(book, "manifest 조회");
        for (Chapter chapter : chapters) {
            if (!locatorSupport.hasLocatorMetadata(chapter) || chapter.getTotalCodePoints() == null) {
                throw new GeneralException(
                        ErrorStatus._FORBIDDEN,
                        "api.contract.mode=v2_only_strict 상태에서는 모든 chapter에 locator 메타가 준비되어 있어야 합니다."
                );
            }
        }
        for (Event event : events) {
            ensureEventLocatorReady(event, "manifest 조회");
        }
    }

    public void ensureEventLocatorReady(Event event, String operationName) {
        if (!apiContractProperties.getResolvedMode().isStrict()) {
            return;
        }

        if (event.getStartTxtOffset() == null || event.getEndTxtOffset() == null
                || event.getStartBlockIndex() == null || event.getStartOffset() == null
                || event.getEndBlockIndex() == null || event.getEndOffset() == null) {
            throw new GeneralException(
                    ErrorStatus._FORBIDDEN,
                    "api.contract.mode=v2_only_strict 상태에서는 " + operationName + " 응답에 event locator/txtOffset 값이 모두 존재해야 합니다."
            );
        }
    }

    public boolean isV2Request(HttpServletRequest request) {
        return request != null
                && request.getRequestURI() != null
                && request.getRequestURI().startsWith("/api/v2/");
    }

    private void ensureBookLocatorVersion(Book book, String operationName) {
        if (book == null) {
            return;
        }
        if (StringUtils.hasText(book.getLocatorVersion())) {
            return;
        }

        throw new GeneralException(
                ErrorStatus._FORBIDDEN,
                "locator.version 이 없는 책에서는 " + operationName + " 를 strict 모드로 수행할 수 없습니다."
        );
    }
}
