package com.kw.readwith.repository;

import com.kw.readwith.domain.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookRepository extends JpaRepository<Book, Long> {

    /**
     * summary 필드가 false인 모든 Book 엔터티를 조회합니다.
     * @return 요약이 없는 책 목록
     */
    List<Book> findBySummaryIsFalse();

    /**
     * 접근 가능한 도서 목록 조회 (사용자별)
     * summary = true AND (isDefault = true OR uploadedBy.id = userId)
     */
    @Query("SELECT b FROM Book b WHERE b.summary = true AND (b.isDefault = true OR b.uploadedBy.id = :userId)")
    List<Book> findAccessibleBooks(@Param("userId") Long userId);

    /**
     * 접근 가능한 도서 목록 조회 (비로그인 사용자 - 기본 제공만)
     * summary = true AND isDefault = true
     */
    List<Book> findBySummaryTrueAndIsDefaultTrue();

    /**
     * 단일 도서 접근 확인 (사용자별)
     * summary = true AND (isDefault = true OR uploadedBy.id = userId)
     */
    @Query("SELECT b FROM Book b WHERE b.id = :bookId AND b.summary = true AND (b.isDefault = true OR b.uploadedBy.id = :userId)")
    Optional<Book> findAccessibleBook(@Param("bookId") Long bookId, @Param("userId") Long userId);

    /**
     * 단일 도서 접근 확인 (비로그인 사용자 - 기본 제공만)
     * summary = true AND isDefault = true
     */
    Optional<Book> findByIdAndSummaryTrueAndIsDefaultTrue(Long id);
}