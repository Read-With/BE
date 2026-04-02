package com.kw.readwith.service;

import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.config.V2TransitionGuard;
import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.Chapter;
import com.kw.readwith.domain.User;
import com.kw.readwith.domain.mapping.UserReadState;
import com.kw.readwith.dto.common.LocatorDTO;
import com.kw.readwith.dto.progress.ProgressResponseDTO;
import com.kw.readwith.dto.progress.SaveProgressRequestDTO;
import com.kw.readwith.repository.BookRepository;
import com.kw.readwith.repository.ChapterRepository;
import com.kw.readwith.repository.UserReadStateRepository;
import com.kw.readwith.repository.UserRepository;
import com.kw.readwith.util.LocatorSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserReadStateService {

    private final UserReadStateRepository userReadStateRepository;
    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final ChapterRepository chapterRepository;
    private final LocatorSupport locatorSupport;
    private final V2TransitionGuard transitionGuard;
    private final BookAccessPolicy bookAccessPolicy;

    @Transactional
    public ProgressResponseDTO saveProgress(Long userId, SaveProgressRequestDTO requestDTO) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.USER_NOT_FOUND));
        Book book = bookRepository.findById(requestDTO.getBookId())
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));

        bookAccessPolicy.ensureReadable(book, user.getId());
        transitionGuard.ensureLocatorWritesEnabled("progress 저장");

        LocatorDTO locator = requestDTO.getStartLocator();
        Chapter chapter = validateLocator(book, locator);
        transitionGuard.ensureLocatorMetadataReady(book, chapter, "progress 저장");
        locatorSupport.toTxtOffset(chapter, locator);

        UserReadState existing = userReadStateRepository.findByUserIdAndBookId(userId, requestDTO.getBookId())
                .orElse(null);

        UserReadState userReadState = UserReadState.builder()
                .id(existing != null ? existing.getId() : null)
                .user(user)
                .book(book)
                .lastLocatorJson(locatorSupport.writeLocator(locator))
                .build();

        return convertToResponseDTO(userReadStateRepository.save(userReadState));
    }

    public ProgressResponseDTO getProgress(Long userId, Long bookId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.USER_NOT_FOUND));
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));

        bookAccessPolicy.ensureReadable(book, user.getId());

        UserReadState userReadState = userReadStateRepository.findByUserIdAndBookId(userId, bookId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.PROGRESS_NOT_FOUND));

        return convertToResponseDTO(userReadState);
    }

    public List<ProgressResponseDTO> getAllProgress(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.USER_NOT_FOUND));

        return userReadStateRepository.findByUserId(userId).stream()
                .filter(userReadState -> bookAccessPolicy.isReadable(userReadState.getBook(), user.getId()))
                .map(this::convertToResponseDTO)
                .toList();
    }

    @Transactional
    public void deleteProgress(Long userId, Long bookId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.USER_NOT_FOUND));
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));

        bookAccessPolicy.ensureReadable(book, user.getId());

        if (!userReadStateRepository.existsByUserIdAndBookId(userId, bookId)) {
            throw new GeneralException(ErrorStatus.PROGRESS_DELETE_FAILED);
        }
        userReadStateRepository.deleteByUserIdAndBookId(userId, bookId);
    }

    private ProgressResponseDTO convertToResponseDTO(UserReadState userReadState) {
        LocatorDTO locator = locatorSupport.readLocator(userReadState.getLastLocatorJson());
        Integer startTxtOffset = resolveTxtOffset(userReadState.getBook(), locator);

        return ProgressResponseDTO.builder()
                .bookId(userReadState.getBook().getId())
                .startLocator(locator)
                .endLocator(null)
                .startTxtOffset(startTxtOffset)
                .endTxtOffset(null)
                .locatorVersion(userReadState.getBook().getLocatorVersion())
                .updatedAt(userReadState.getUpdatedAt())
                .build();
    }

    private Chapter validateLocator(Book book, LocatorDTO locator) {
        if (locator == null || locator.getChapterIndex() == null) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "locator.chapterIndex는 필수입니다.");
        }

        return chapterRepository.findByBookIdAndIdx(book.getId(), locator.getChapterIndex())
                .orElseThrow(() -> new GeneralException(ErrorStatus.CHAPTER_NOT_FOUND));
    }

    private Integer resolveTxtOffset(Book book, LocatorDTO locator) {
        if (locator == null || locator.getChapterIndex() == null) {
            return null;
        }

        Chapter chapter = validateLocator(book, locator);
        return locatorSupport.toTxtOffset(chapter, locator);
    }
}
