package com.kw.readwith.repository;

import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.Character;
import com.kw.readwith.domain.enums.ImageGenerationStatus;
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

    /**
     * 캐릭터의 프로필 이미지 URL 업데이트
     */
    @Modifying
    @Query("UPDATE Character c SET c.profileImage = :imageUrl WHERE c.id = :characterId")
    void updateProfileImage(@Param("characterId") Long characterId, @Param("imageUrl") String imageUrl);

    /**
     * 캐릭터의 이미지 생성 상태 업데이트
     */
    @Modifying
    @Query("UPDATE Character c SET c.imageGenerationStatus = :status WHERE c.id = :characterId")
    void updateImageGenerationStatus(@Param("characterId") Long characterId, @Param("status") ImageGenerationStatus status);

    /**
     * 캐릭터의 프로필 이미지 URL과 상태를 동시에 업데이트
     */
    @Modifying
    @Query("UPDATE Character c SET c.profileImage = :imageUrl, c.imageGenerationStatus = :status WHERE c.id = :characterId")
    void updateProfileImageAndStatus(@Param("characterId") Long characterId, 
                                      @Param("imageUrl") String imageUrl, 
                                      @Param("status") ImageGenerationStatus status);

    /**
     * ID로 Character 조회 시 Book을 함께 fetch join (LAZY 로딩 문제 해결)
     */
    @Query("SELECT c FROM Character c LEFT JOIN FETCH c.book WHERE c.id = :id")
    Optional<Character> findByIdWithBook(@Param("id") Long id);
}
