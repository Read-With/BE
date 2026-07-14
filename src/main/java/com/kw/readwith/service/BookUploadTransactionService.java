package com.kw.readwith.service;

import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.User;
import com.kw.readwith.domain.processing.ProcessingJob;
import com.kw.readwith.repository.BookRepository;
import com.kw.readwith.repository.UserRepository;
import com.kw.readwith.service.normalization.NormalizationJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookUploadTransactionService {

    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final NormalizationJobService normalizationJobService;

    @Transactional
    public Book createUploadBook(Long userId, String title, String author, String language) {
        User uploader = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.USER_NOT_FOUND));

        Book book = Book.builder()
                .title(title)
                .author(author)
                .language(language)
                .isDefault(false)
                .coverImgUrl(null)
                .uploadedBy(uploader)
                .summary(false)
                .build();

        return bookRepository.save(book);
    }

    @Transactional
    public BookUploadCompletion completeUpload(
            Long bookId,
            String sourceVersion,
            String sourcePath,
            String coverUrl
    ) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));

        book.assignUploadedSource(sourcePath);
        if (coverUrl != null && !coverUrl.isBlank()) {
            book.updateCoverImage(coverUrl);
        }
        book.markNormalizationQueued();
        book.resetAnalysisStatus();

        ProcessingJob job = normalizationJobService.createQueuedJob(book, sourceVersion, "UPLOAD");
        return new BookUploadCompletion(book, job.getId());
    }

    @Transactional
    public void markUploadFailed(Long bookId) {
        bookRepository.findById(bookId)
                .ifPresent(Book::markNormalizationFailed);
    }

    public record BookUploadCompletion(Book book, Long jobId) {
    }
}
