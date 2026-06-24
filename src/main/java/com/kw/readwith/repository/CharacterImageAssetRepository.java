package com.kw.readwith.repository;

import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.Character;
import com.kw.readwith.domain.CharacterImageAsset;
import com.kw.readwith.domain.enums.CharacterImageAssetRole;
import com.kw.readwith.domain.enums.CharacterImageAssetStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CharacterImageAssetRepository extends JpaRepository<CharacterImageAsset, Long> {

    Optional<CharacterImageAsset> findByIdAndBook(Long id, Book book);

    List<CharacterImageAsset> findByBookOrderByCreatedAtDesc(Book book);

    List<CharacterImageAsset> findByCharacterOrderByCreatedAtDesc(Character character);

    Optional<CharacterImageAsset> findFirstByCharacterOrderByCreatedAtDesc(Character character);

    Optional<CharacterImageAsset> findFirstByCharacterAndStatusOrderByCreatedAtDesc(
            Character character,
            CharacterImageAssetStatus status
    );

    List<CharacterImageAsset> findByBookAndStatusIn(Book book, Collection<CharacterImageAssetStatus> statuses);

    @Query("SELECT COALESCE(MAX(a.attemptNo), 0) FROM CharacterImageAsset a " +
            "WHERE a.character = :character AND a.assetRole = :assetRole")
    int findMaxAttemptNo(@Param("character") Character character,
                         @Param("assetRole") CharacterImageAssetRole assetRole);

    @Modifying
    @Query("UPDATE CharacterImageAsset a SET a.status = com.kw.readwith.domain.enums.CharacterImageAssetStatus.STALE_REFERENCE " +
            "WHERE a.book = :book " +
            "AND a.sourceReferenceAsset = :sourceReferenceAsset " +
            "AND a.status IN :statuses")
    int markDerivedAssetsStale(@Param("book") Book book,
                               @Param("sourceReferenceAsset") CharacterImageAsset sourceReferenceAsset,
                               @Param("statuses") Collection<CharacterImageAssetStatus> statuses);
}
