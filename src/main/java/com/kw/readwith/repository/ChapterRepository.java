package com.kw.readwith.repository;

import com.kw.readwith.domain.Chapter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChapterRepository extends JpaRepository<Chapter, Long> {

    @Query("SELECT c FROM Chapter c WHERE c.povSummariesCached = false AND c.summaryUploadUrl IS NOT NULL")
    List<Chapter> findUnsummarizedChapters();

    @Query("SELECT c FROM Chapter c JOIN FETCH c.book WHERE c.book.id = :bookId AND c.idx = :idx")
    Optional<Chapter> findByBookIdAndIdx(@Param("bookId") Long bookId, @Param("idx") int idx);

    List<Chapter> findByBookId(Long bookId);

}