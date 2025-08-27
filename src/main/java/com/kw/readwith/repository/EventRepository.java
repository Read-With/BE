package com.kw.readwith.repository;

import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.Chapter;
import com.kw.readwith.domain.Event;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    boolean existsByBookAndChapter(Book book, Chapter chapter);
    Optional<Event> findByChapterAndIdx(Chapter chapter, Integer idx);

    // Event를 찾는 메소드
    Optional<Event> findByBookAndChapterAndIdx(Book book, Chapter chapter, Integer idx);
}
