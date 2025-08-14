package com.kw.readwith.service;

import com.kw.readwith.domain.Chapter;
import com.kw.readwith.repository.ChapterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChapterService {

    private final ChapterRepository chapterRepository;

    public List<Chapter> findUnsummarizedChapters() {
        return chapterRepository.findUnsummarizedChapters();
    }

    @Transactional
    public void updateChapterSummary(Long bookId, int idx, String summary) {
        Chapter chapter = chapterRepository.findByBookIdAndIdx(bookId, idx)
                .orElseThrow(() -> new IllegalArgumentException("Chapter not found"));
        chapter.setSummaryText(summary);
        // 변경 감지에 의해 트랜잭션이 커밋될 때 업데이트됩니다.
    }
}
