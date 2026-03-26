package com.kw.readwith.service;

import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.User;
import com.kw.readwith.domain.enums.NormalizationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BookAccessPolicyTest {

    private final BookAccessPolicy bookAccessPolicy = new BookAccessPolicy();

    @Test
    @DisplayName("정규화 완료된 기본 책은 로그인 사용자에게 읽기 허용된다")
    void defaultReadyBookIsReadable() {
        Book book = Book.builder()
                .id(1L)
                .isDefault(true)
                .normalizationStatus(NormalizationStatus.READY)
                .build();

        assertThatCode(() -> bookAccessPolicy.ensureReadable(book, 99L))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("정규화 미완료 책은 접근 가능 사용자여도 읽기 차단된다")
    void unreadableWhenNormalizationNotReady() {
        User owner = User.builder().id(7L).build();
        Book book = Book.builder()
                .id(1L)
                .isDefault(false)
                .uploadedBy(owner)
                .normalizationStatus(NormalizationStatus.PROCESSING)
                .build();

        assertThrows(GeneralException.class, () -> bookAccessPolicy.ensureReadable(book, 7L));
    }

    @Test
    @DisplayName("비공개 업로드 책은 업로더만 접근할 수 있다")
    void privateUploadedBookOnlyOwnerCanAccess() {
        User owner = User.builder().id(7L).build();
        Book book = Book.builder()
                .id(1L)
                .isDefault(false)
                .uploadedBy(owner)
                .normalizationStatus(NormalizationStatus.READY)
                .build();

        assertThatCode(() -> bookAccessPolicy.ensureReadable(book, 7L))
                .doesNotThrowAnyException();
        assertThrows(GeneralException.class, () -> bookAccessPolicy.ensureReadable(book, 8L));
    }
}
