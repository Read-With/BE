package com.kw.readwith.service;

import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.domain.Book;
import org.springframework.stereotype.Service;

@Service
public class BookAccessPolicy {

    public void ensureReadable(Book book, Long userId) {
        ensureUserCanAccess(book, userId);
        ensureNormalizationReady(book);
    }

    public void ensureUserCanAccess(Book book, Long userId) {
        if (canUserAccess(book, userId)) {
            return;
        }
        throw new GeneralException(ErrorStatus.BOOK_ACCESS_DENIED);
    }

    public void ensureNormalizationReady(Book book) {
        if (book != null && book.isNormalizationReady()) {
            return;
        }
        throw new GeneralException(ErrorStatus._BAD_REQUEST, "정규화가 완료되지 않은 책입니다.");
    }

    public boolean isReadable(Book book, Long userId) {
        return canUserAccess(book, userId) && book != null && book.isNormalizationReady();
    }

    public boolean canUserAccess(Book book, Long userId) {
        return book != null;
    }
}
