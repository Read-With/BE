package com.kw.readwith.repository;

import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.Character;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CharacterRepository extends JpaRepository<Character, Long> {

    // Book과 characterId로 Character를 찾는 새로운 메소드 추가
    Optional<Character> findByBookAndCharacterId(Book book, Long characterId);
}