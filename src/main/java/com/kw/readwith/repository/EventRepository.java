package com.kw.readwith.repository;

import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.Chapter;
import com.kw.readwith.domain.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    boolean existsByBookAndChapter(Book book, Chapter chapter);
    Optional<Event> findByChapterAndIdx(Chapter chapter, Integer idx);

    // Event를 찾는 메소드
    Optional<Event> findByBookAndChapterAndIdx(Book book, Chapter chapter, Integer idx);
    
    /**
     * 특정 챕터의 모든 이벤트를 인덱스 순으로 조회
     */
    List<Event> findByChapterOrderByIdx(Chapter chapter);
    
    /**
     * 특정 책의 모든 이벤트를 챕터, 이벤트 순으로 조회
     */
    @Query("SELECT e FROM Event e JOIN e.chapter c " +
           "WHERE e.book = :book " +
           "ORDER BY c.idx ASC, e.idx ASC")
    List<Event> findByBookOrderByChapterIdxAscIdxAsc(@Param("book") Book book);

    /**
     * 특정 챕터의 마지막 이벤트 조회 (가장 큰 idx)
     */
    @Query("SELECT e FROM Event e " +
           "WHERE e.chapter = :chapter " +
           "ORDER BY e.idx DESC " +
           "LIMIT 1")
    Optional<Event> findLastEventByChapter(@Param("chapter") Chapter chapter);

    /**
     * 특정 책의 각 챕터별 마지막 이벤트들 조회 (글자수 계산용)
     */
    @Query("SELECT e FROM Event e " +
           "WHERE e.book = :book " +
           "AND e.idx = (SELECT MAX(e2.idx) FROM Event e2 WHERE e2.chapter = e.chapter) " +
           "ORDER BY e.chapter.idx ASC")
    List<Event> findLastEventsByBook(@Param("book") Book book);

    /**
     * 특정 책의 특정 챕터까지의 각 챕터별 마지막 이벤트들 조회
     */
    @Query("SELECT e FROM Event e " +
           "WHERE e.book = :book AND e.chapter.idx <= :uptoChapter " +
           "AND e.idx = (SELECT MAX(e2.idx) FROM Event e2 WHERE e2.chapter = e.chapter) " +
           "ORDER BY e.chapter.idx ASC")
    List<Event> findLastEventsByChaptersUpTo(@Param("book") Book book, @Param("uptoChapter") Integer uptoChapter);

    boolean existsByChapter(Chapter chapter);

    @Modifying
    void deleteByChapter(Chapter chapter);
}
