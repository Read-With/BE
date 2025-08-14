package com.kw.readwith.repository;

import com.kw.readwith.domain.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookRepository extends JpaRepository<Book, Long> {

    /**
     * summary 필드가 false인 모든 Book 엔터티를 조회합니다.
     * @return 요약이 없는 책 목록
     */
    List<Book> findBySummaryIsFalse();

    /**
     * infoUploaded 필드가 true인 모든 Book 엔터티를 조회합니다.
     * @return 정보가 업로드된 책 목록
     */
    List<Book> findByInfoUploadedTrue();
}