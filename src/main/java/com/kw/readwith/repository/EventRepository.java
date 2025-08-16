package com.kw.readwith.repository;

import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.Chapter;
import com.kw.readwith.domain.Event;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, Long> {

    // Book과 Chapter를 기준으로 데이터 존재 여부를 확인.
    boolean existsByBookAndChapter(Book book, Chapter chapter);
}