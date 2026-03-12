package com.kw.readwith.service;

import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserReadStateService {

    private final UserReadStateRepository userReadStateRepository;
    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final ChapterRepository chapterRepository;
    private final LocatorSupport locatorSupport;

    // 진도 저장 또는 업데이트
    @Transactional
    public ProgressResponseDTO saveProgress(Long userId, SaveProgressRequestDTO requestDTO) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.USER_NOT_FOUND));
        
        Book book = bookRepository.findById(requestDTO.getBookId())
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));

        // 책 접근 권한 확인
        if (!hasAccessToBook(user, book)) {
            throw new GeneralException(ErrorStatus.BOOK_ACCESS_DENIED);
        }
        LocatorDTO locator = requestDTO.getLocator();
        Chapter chapter = validateLocator(book, locator);
        locatorSupport.toTxtOffset(chapter, locator);

        // 기존 읽기 상태가 있는지 확인
        UserReadState userReadState = userReadStateRepository.findByUserIdAndBookId(userId, requestDTO.getBookId())
                .orElse(null);

        if (userReadState == null) {
            // 새로운 읽기 상태 생성
            userReadState = UserReadState.builder()
                    .user(user)
                    .book(book)
                    .lastLocatorJson(locatorSupport.writeLocator(locator))
                    .build();
        } else {
            // 기존 읽기 상태 업데이트
            userReadState = UserReadState.builder()
                    .id(userReadState.getId())
                    .user(user)
                    .book(book)
                    .lastLocatorJson(locatorSupport.writeLocator(locator))
                    .build();
        }

        UserReadState savedUserReadState = userReadStateRepository.save(userReadState);
        return convertToResponseDTO(savedUserReadState);
    }

    // 특정 책의 진도 조회
    public ProgressResponseDTO getProgress(Long userId, Long bookId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.USER_NOT_FOUND));
        
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));

        // 책 접근 권한 확인
        if (!hasAccessToBook(user, book)) {
            throw new GeneralException(ErrorStatus.BOOK_ACCESS_DENIED);
        }

        UserReadState userReadState = userReadStateRepository.findByUserIdAndBookId(userId, bookId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.PROGRESS_NOT_FOUND));
        
        return convertToResponseDTO(userReadState);
    }

    // 사용자의 모든 진도 조회 (접근 가능한 책만)
    public List<ProgressResponseDTO> getAllProgress(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.USER_NOT_FOUND));

        List<UserReadState> userReadStateList = userReadStateRepository.findByUserId(userId);
        
        // 접근 가능한 책의 읽기 상태만 필터링
        return userReadStateList.stream()
                .filter(userReadState -> hasAccessToBook(user, userReadState.getBook()))
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    // 진도 삭제
    @Transactional
    public void deleteProgress(Long userId, Long bookId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.USER_NOT_FOUND));
        
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));

        // 책 접근 권한 확인
        if (!hasAccessToBook(user, book)) {
            throw new GeneralException(ErrorStatus.BOOK_ACCESS_DENIED);
        }

        if (!userReadStateRepository.existsByUserIdAndBookId(userId, bookId)) {
            throw new GeneralException(ErrorStatus.PROGRESS_DELETE_FAILED);
        }
        userReadStateRepository.deleteByUserIdAndBookId(userId, bookId);
    }

    // 책 접근 권한 확인 메서드
    private boolean hasAccessToBook(User user, Book book) {
        // uploadedBy가 null이면 모든 사용자가 접근 가능 (서버 기본 제공)
        if (book.getUploadedBy() == null) {
            return true;
        }
        
        // uploadedBy가 있으면 해당 사용자만 접근 가능
        return book.getUploadedBy().getId().equals(user.getId());
    }

    // DTO 변환 메서드
    private ProgressResponseDTO convertToResponseDTO(UserReadState userReadState) {
        return ProgressResponseDTO.builder()
                .bookId(userReadState.getBook().getId())
                .locator(locatorSupport.readLocator(userReadState.getLastLocatorJson()))
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
}
