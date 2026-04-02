package com.kw.readwith.service;

import com.kw.readwith.config.V2TransitionGuard;
import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.Chapter;
import com.kw.readwith.domain.User;
import com.kw.readwith.domain.enums.NormalizationStatus;
import com.kw.readwith.domain.enums.Provider;
import com.kw.readwith.domain.mapping.UserReadState;
import com.kw.readwith.dto.common.LocatorDTO;
import com.kw.readwith.dto.progress.ProgressResponseDTO;
import com.kw.readwith.dto.progress.SaveProgressRequestDTO;
import com.kw.readwith.repository.BookRepository;
import com.kw.readwith.repository.ChapterRepository;
import com.kw.readwith.repository.UserReadStateRepository;
import com.kw.readwith.repository.UserRepository;
import com.kw.readwith.util.LocatorSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserReadStateServiceTest {

    @Mock
    private UserReadStateRepository userReadStateRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private ChapterRepository chapterRepository;

    @Mock
    private LocatorSupport locatorSupport;

    @Mock
    private V2TransitionGuard transitionGuard;

    @Mock
    private BookAccessPolicy bookAccessPolicy;

    @InjectMocks
    private UserReadStateService userReadStateService;

    @Test
    @DisplayName("saveProgress returns v2 fields from a point locator")
    void saveProgressReturnsEnrichedResponse() {
        User user = sampleUser();
        Book book = sampleBook();
        Chapter chapter = sampleChapter(book);
        LocatorDTO locator = sampleLocator();
        SaveProgressRequestDTO request = SaveProgressRequestDTO.builder()
                .bookId(10L)
                .startLocator(locator)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(bookRepository.findById(10L)).thenReturn(Optional.of(book));
        when(chapterRepository.findByBookIdAndIdx(10L, 2)).thenReturn(Optional.of(chapter));
        when(locatorSupport.toTxtOffset(chapter, locator)).thenReturn(120);
        when(userReadStateRepository.findByUserIdAndBookId(1L, 10L)).thenReturn(Optional.empty());
        when(locatorSupport.writeLocator(locator)).thenReturn("{locator}");
        when(locatorSupport.readLocator("{locator}")).thenReturn(locator);
        when(userReadStateRepository.save(any(UserReadState.class))).thenAnswer(invocation -> {
            UserReadState saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "updatedAt", LocalDateTime.of(2026, 4, 2, 10, 0));
            return saved;
        });

        ProgressResponseDTO response = userReadStateService.saveProgress(1L, request);

        assertThat(response.getBookId()).isEqualTo(10L);
        assertThat(response.getStartLocator()).isEqualTo(locator);
        assertThat(response.getLocator()).isEqualTo(locator);
        assertThat(response.getEndLocator()).isNull();
        assertThat(response.getStartTxtOffset()).isEqualTo(120);
        assertThat(response.getEndTxtOffset()).isNull();
        assertThat(response.getLocatorVersion()).isEqualTo("locator-v2");
    }

    @Test
    @DisplayName("getProgress exposes locatorVersion and txtOffset for stored progress")
    void getProgressReturnsEnrichedResponse() {
        User user = sampleUser();
        Book book = sampleBook();
        Chapter chapter = sampleChapter(book);
        LocatorDTO locator = sampleLocator();
        UserReadState userReadState = UserReadState.builder()
                .id(50L)
                .user(user)
                .book(book)
                .lastLocatorJson("{locator}")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(bookRepository.findById(10L)).thenReturn(Optional.of(book));
        when(userReadStateRepository.findByUserIdAndBookId(1L, 10L)).thenReturn(Optional.of(userReadState));
        when(locatorSupport.readLocator("{locator}")).thenReturn(locator);
        when(chapterRepository.findByBookIdAndIdx(10L, 2)).thenReturn(Optional.of(chapter));
        when(locatorSupport.toTxtOffset(chapter, locator)).thenReturn(120);

        ProgressResponseDTO response = userReadStateService.getProgress(1L, 10L);

        assertThat(response.getStartLocator()).isEqualTo(locator);
        assertThat(response.getStartTxtOffset()).isEqualTo(120);
        assertThat(response.getLocatorVersion()).isEqualTo("locator-v2");
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
                .locatorVersion("locator-v2")
                .build();
    }

    private Chapter sampleChapter(Book book) {
        return Chapter.builder()
                .id(20L)
                .book(book)
                .idx(2)
                .build();
    }

    private LocatorDTO sampleLocator() {
        return LocatorDTO.builder()
                .chapterIndex(2)
                .blockIndex(3)
                .offset(4)
                .build();
    }
}
