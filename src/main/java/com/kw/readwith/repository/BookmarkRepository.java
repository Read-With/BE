package com.kw.readwith.repository;

import com.kw.readwith.domain.Bookmark;
import org.springframework.data.jpa.repository.JpaRepository;
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
    Optional<Bookmark> findByIdAndUserId(Long bookmarkId, Long userId);

    /**
     * 사용자의 특정 책 북마크 개수
     */
    long countByUserIdAndBookId(Long userId, Long bookId);
}
