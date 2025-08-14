package com.kw.readwith.repository;

import com.kw.readwith.domain.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookRepository extends JpaRepository<Book, Long> {

    /**
     * summary 필드가 false인 모든 Book 엔티티를 조회합니다.
     * @return 요약이 없는 책 목록
     */
    List<Book> findBySummaryIsFalse();
} 