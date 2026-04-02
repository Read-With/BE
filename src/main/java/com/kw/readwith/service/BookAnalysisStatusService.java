package com.kw.readwith.service;

import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.Chapter;
import com.kw.readwith.repository.BookRepository;
import com.kw.readwith.repository.ChapterRepository;
import com.kw.readwith.repository.CharacterRepository;
import com.kw.readwith.repository.EventCharacterStatRepository;
import com.kw.readwith.repository.EventRelationshipEdgeRepository;
import com.kw.readwith.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BookAnalysisStatusService {

    private final BookRepository bookRepository;
    private final ChapterRepository chapterRepository;
    private final CharacterRepository characterRepository;
    private final EventRepository eventRepository;
    private final EventRelationshipEdgeRepository eventRelationshipEdgeRepository;
    private final EventCharacterStatRepository eventCharacterStatRepository;

    @Transactional
    public void refreshStatus(Long bookId) {
        Book book = bookRepository.findById(bookId).orElse(null);
        if (book == null) {
            return;
        }

        if (isAnalysisComplete(book)) {
            book.markAnalysisReady();
            return;
        }

        book.resetAnalysisStatus();
    }

    @Transactional
    public void resetToNone(Long bookId) {
        bookRepository.findById(bookId).ifPresent(Book::resetAnalysisStatus);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRejectedIfPending(Long bookId) {
        bookRepository.findById(bookId).ifPresent(book -> {
            if (!book.isAnalysisReady()) {
                book.markAnalysisRejected();
            }
        });
    }

    private boolean isAnalysisComplete(Book book) {
        if (!book.isNormalizationReady()) {
            return false;
        }
        if (!characterRepository.existsByBook(book)) {
            return false;
        }
        if (!eventRepository.existsByBook(book)) {
            return false;
        }

        List<Chapter> chapters = chapterRepository.findByBookId(book.getId());
        if (chapters.isEmpty()) {
            return false;
        }
        if (chapters.stream().anyMatch(chapter -> !chapter.isPovSummariesCached())) {
            return false;
        }

        return eventRelationshipEdgeRepository.existsByBook(book)
                || eventCharacterStatRepository.existsByBook(book);
    }
}
