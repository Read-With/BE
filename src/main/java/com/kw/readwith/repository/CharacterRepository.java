package com.kw.readwith.repository;

import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.Character;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CharacterRepository extends JpaRepository<Character, Long> {

    // Book과 characterId로 Character를 찾는 새로운 메소드 추가
    Optional<Character> findByBookAndCharacterId(Book book, Long characterId);

    // Book과 name으로 Character를 찾는 메소드
    Optional<Character> findByBookAndName(Book book, String name);
    /**
     * 특정 책의 모든 인물을 주요 인물 우선, 이름 순으로 조회
     */
    @Query("SELECT c FROM Character c " +
           "WHERE c.book = :book " +
           "ORDER BY c.isMainCharacter DESC, c.name ASC")
    List<Character> findByBookOrderByIsMainCharacterDescNameAsc(@Param("book") Book book);

    boolean existsByBook(Book book);

    @Modifying
    int deleteByBook(Book book);
}
