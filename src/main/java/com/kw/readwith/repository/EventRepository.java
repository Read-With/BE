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

    /**
     * 이벤트를 찾는 메서드
     */
    @Query("SELECT e FROM Event e JOIN e.chapter c WHERE e.book.id = :bookId AND c.idx = :chapterIdx AND e.idx = :eventIdx")
    Optional<Event> findByBookIdAndChapterIdxAndEventIdx(@Param("bookId") Long bookId, @Param("chapterIdx") int chapterIdx, @Param("eventIdx") int eventIdx);

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

    boolean existsByChapter(Chapter chapter);

    @Modifying
    int deleteByChapter(Chapter chapter);
}
