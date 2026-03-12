package com.kw.readwith.repository;

import com.kw.readwith.domain.Book;
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
    @Query("SELECT c FROM Chapter c JOIN FETCH c.book WHERE c.povSummariesCached = false")
    List<Chapter> findUnsummarizedChapters();

    // bookId와 chapterIdx로 확인
    @Query("SELECT c FROM Chapter c JOIN FETCH c.book WHERE c.book.id = :bookId AND c.idx = :idx")
    Optional<Chapter> findByBookIdAndIdx(@Param("bookId") Long bookId, @Param("idx") int idx);

    List<Chapter> findByBookId(Long bookId);

    /**
     * 특정 책의 최대 챕터 인덱스 조회
     */
    @Query("SELECT MAX(c.idx) FROM Chapter c WHERE c.book = :book")
    Integer findMaxChapterIdxByBook(@Param("book") Book book);

    /**
     * 특정 책의 각 챕터별 글자수 조회 (totalCodePoints 우선, 없으면 마지막 이벤트 endPos)
     */
    @Query("SELECT c.idx, COALESCE(c.totalCodePoints, COALESCE(MAX(e.endPos), 0)) FROM Chapter c " +
           "LEFT JOIN c.events e WHERE c.book = :book " +
           "GROUP BY c.idx, c.totalCodePoints ORDER BY c.idx")
    List<Object[]> findChapterLengthsByBook(@Param("book") Book book);

}
