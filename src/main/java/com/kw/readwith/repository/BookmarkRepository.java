package com.kw.readwith.repository;

import com.kw.readwith.domain.Bookmark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    /**
     * 사용자의 특정 책에 대한 북마크 목록 조회 (생성일시 기준 정렬)
     */
    List<Bookmark> findByUserIdAndBookIdOrderByCreatedAtDesc(Long userId, Long bookId);

    /**
     * 사용자의 특정 책에 대한 북마크 목록 조회 (생성일시 오름차순)
     */
    List<Bookmark> findByUserIdAndBookIdOrderByCreatedAtAsc(Long userId, Long bookId);

    /**
     * 사용자의 모든 북마크 조회
     */
    List<Bookmark> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * 특정 북마크가 해당 사용자의 것인지 확인
     */
    @Query("SELECT b FROM Bookmark b WHERE b.id = :bookmarkId AND b.user.id = :userId")
    Optional<Bookmark> findByIdAndUserId(@Param("bookmarkId") Long bookmarkId, @Param("userId") Long userId);

    /**
     * 중복 CFI 위치 북마크 확인 (시작 CFI 기준)
     */
    Optional<Bookmark> findByUserIdAndBookIdAndStartCfi(Long userId, Long bookId, String startCfi);

    /**
     * 특정 범위와 겹치는 북마크 확인
     */
    @Query("SELECT b FROM Bookmark b WHERE b.user.id = :userId AND b.book.id = :bookId AND " +
           "((b.startCfi = :startCfi) OR (b.endCfi IS NOT NULL AND b.endCfi = :endCfi) OR " +
           "(b.startCfi = :startCfi AND b.endCfi = :endCfi))")
    List<Bookmark> findOverlappingBookmarks(@Param("userId") Long userId, 
                                          @Param("bookId") Long bookId, 
                                          @Param("startCfi") String startCfi, 
                                          @Param("endCfi") String endCfi);

    /**
     * 사용자의 특정 책 북마크 개수
     */
    long countByUserIdAndBookId(Long userId, Long bookId);
}
