package com.kw.readwith.repository;

import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.enums.NormalizationStatus;
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

    List<Book> findByNormalizationStatus(NormalizationStatus normalizationStatus);

    Optional<Book> findByIdAndNormalizationStatus(Long id, NormalizationStatus normalizationStatus);

    /**
     * 접근 가능한 도서 목록 조회 (사용자별)
     * 기본 제공 도서는 READY만 노출, 업로더 본인 도서는 상태와 무관하게 노출
     */
    @Query("SELECT b FROM Book b WHERE (b.isDefault = true AND b.normalizationStatus = :ready) OR b.uploadedBy.id = :userId")
    List<Book> findAccessibleBooks(@Param("userId") Long userId, @Param("ready") NormalizationStatus ready);

    /**
     * 접근 가능한 도서 목록 조회 (비로그인 사용자 - 기본 제공만)
     * 기본 제공 + 정규화 완료만
     */
    List<Book> findByNormalizationStatusAndIsDefaultTrue(NormalizationStatus normalizationStatus);

    /**
     * 단일 도서 접근 확인 (사용자별)
     * 기본 제공 도서는 READY만 노출, 업로더 본인 도서는 상태와 무관하게 노출
     */
    @Query("SELECT b FROM Book b WHERE b.id = :bookId AND ((b.isDefault = true AND b.normalizationStatus = :ready) OR b.uploadedBy.id = :userId)")
    Optional<Book> findAccessibleBook(@Param("bookId") Long bookId,
                                      @Param("userId") Long userId,
                                      @Param("ready") NormalizationStatus ready);

    /**
     * 단일 도서 접근 확인 (비로그인 사용자 - 기본 제공 + 정규화 완료만)
     */
    Optional<Book> findByIdAndNormalizationStatusAndIsDefaultTrue(Long id, NormalizationStatus normalizationStatus);
}
