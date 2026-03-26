package com.kw.readwith.service.normalization;

import com.kw.readwith.config.EpubNormalizationProperties;
import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.enums.NormalizationVersionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class NormalizationVersionService {

    private final EpubNormalizationProperties epubNormalizationProperties;

    public NormalizationVersionStatus resolveStatus(Book book) {
        if (book == null || !book.isNormalizationReady()) {
            return NormalizationVersionStatus.NOT_READY;
        }
        if (Objects.equals(book.getRuleVersion(), epubNormalizationProperties.getRuleVersion())
                && Objects.equals(book.getLocatorVersion(), epubNormalizationProperties.getLocatorVersion())) {
            return NormalizationVersionStatus.CURRENT;
        }
        return NormalizationVersionStatus.OUTDATED;
    }

    public boolean needsRenormalization(Book book) {
        return resolveStatus(book) == NormalizationVersionStatus.OUTDATED;
    }
}
