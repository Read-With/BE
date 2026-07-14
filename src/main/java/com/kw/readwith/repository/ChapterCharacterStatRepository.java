package com.kw.readwith.repository;

import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.cache.ChapterCharacterStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ChapterCharacterStatRepository extends JpaRepository<ChapterCharacterStat, Long> {

    @Modifying
    @Query("DELETE FROM ChapterCharacterStat stat WHERE stat.chapter IN " +
           "(SELECT chapter FROM Chapter chapter WHERE chapter.book = :book)")
    int deleteByBook(@Param("book") Book book);
}
