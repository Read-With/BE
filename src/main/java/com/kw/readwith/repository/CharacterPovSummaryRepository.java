package com.kw.readwith.repository;

import com.kw.readwith.domain.Chapter;
import com.kw.readwith.domain.mapping.CharacterPovSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CharacterPovSummaryRepository extends JpaRepository<CharacterPovSummary, Long> {

    List<CharacterPovSummary> findByChapterId(Long chapterId);

    @Query("SELECT cps FROM CharacterPovSummary cps " +
           "JOIN FETCH cps.character c " +
           "JOIN FETCH cps.chapter ch " +
           "WHERE cps.book.id = :bookId AND ch.idx = :chapterIdx " +
           "ORDER BY c.isMainCharacter DESC, c.name ASC")
    List<CharacterPovSummary> findByBookIdAndChapterIdx(@Param("bookId") Long bookId, @Param("chapterIdx") Integer chapterIdx);

    boolean existsByChapter(Chapter chapter);

    int deleteByChapter(Chapter chapter);
}
