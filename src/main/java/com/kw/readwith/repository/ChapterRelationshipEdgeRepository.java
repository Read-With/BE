package com.kw.readwith.repository;

import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.mapping.ChapterRelationshipEdge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChapterRelationshipEdgeRepository extends JpaRepository<ChapterRelationshipEdge, Long> {
    
    /**
     * 특정 책의 특정 챕터까지의 모든 관계 엣지 조회 (거시 그래프용)
     */
    @Query("SELECT c FROM ChapterRelationshipEdge c " +
           "WHERE c.book = :book AND c.chapterIdx <= :uptoChapter " +
           "ORDER BY c.chapterIdx ASC")
    List<ChapterRelationshipEdge> findByBookAndChapterIdxLessThanEqual(
            @Param("book") Book book, 
            @Param("uptoChapter") Integer uptoChapter);

    /**
     * 특정 책의 모든 관계 엣지 조회 (챕터 순서대로)
     */
    List<ChapterRelationshipEdge> findByBookOrderByChapterIdxAsc(Book book);

    /**
     * 특정 책의 특정 챕터의 관계 엣지 조회
     */
    List<ChapterRelationshipEdge> findByBookAndChapterIdx(Book book, Integer chapterIdx);
}
