package com.kw.readwith.repository;

import com.kw.readwith.domain.Chapter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChapterRepository extends JpaRepository<Chapter, Long> {

    // POV 요약이 완료되지 않은 모든 챕터를 조회
    @Query("SELECT c FROM Chapter c WHERE c.povSummariesCached = false")
    List<Chapter> findUnsummarizedChapters();

    // bookId와 chapterIdx로 확인
    @Query("SELECT c FROM Chapter c JOIN FETCH c.book WHERE c.book.id = :bookId AND c.idx = :idx")
    Optional<Chapter> findByBookIdAndIdx(@Param("bookId") Long bookId, @Param("idx") int idx);

    List<Chapter> findByBookId(Long bookId);

    /**
     * 특정 책의 최대 챕터 번호 조회
     */
    @Query("SELECT MAX(c.idx) FROM Chapter c WHERE c.book.id = :bookId")
    Integer findMaxChapterIdxByBookId(@Param("bookId") Long bookId);

    /**
     * 특정 책의 모든 챕터를 idx 순으로 조회 (글자수 계산용)
     */
    @Query("SELECT c FROM Chapter c WHERE c.book.id = :bookId ORDER BY c.idx ASC")
    List<Chapter> findByBookIdOrderByIdxAsc(@Param("bookId") Long bookId);

}
