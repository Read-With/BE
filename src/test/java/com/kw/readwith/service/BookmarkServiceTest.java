package com.kw.readwith.service;

import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.config.V2TransitionGuard;
import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.User;
import com.kw.readwith.domain.enums.NormalizationStatus;
import com.kw.readwith.domain.enums.Provider;
import com.kw.readwith.repository.BookRepository;
import com.kw.readwith.repository.BookmarkRepository;
import com.kw.readwith.repository.ChapterRepository;
import com.kw.readwith.repository.UserRepository;
import com.kw.readwith.util.LocatorSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookmarkServiceTest {

    @Mock
    private BookmarkRepository bookmarkRepository;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ChapterRepository chapterRepository;

    @Mock
    private LocatorSupport locatorSupport;

    @Mock
    private V2TransitionGuard transitionGuard;

    @Mock
    private BookAccessPolicy bookAccessPolicy;

    @InjectMocks
    private BookmarkService bookmarkService;

    @Test
    @DisplayName("bookmark list rejects unsupported sort values")
    void getBookmarksRejectsUnsupportedSort() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser()));
        when(bookRepository.findById(10L)).thenReturn(Optional.of(sampleBook()));

        assertThrows(GeneralException.class, () -> bookmarkService.getBookmarks(1L, 10L, "latest"));

        verifyNoInteractions(bookmarkRepository);
    }

    private User sampleUser() {
        return User.builder()
                .id(1L)
                .email("user@example.com")
                .nickname("user")
                .provider(Provider.GOOGLE)
                .providerUid("provider-uid")
                .isAdmin(false)
                .build();
    }

    private Book sampleBook() {
        return Book.builder()
                .id(10L)
                .title("Book")
                .author("Author")
                .language("en")
                .normalizationStatus(NormalizationStatus.READY)
                .build();
    }
}
