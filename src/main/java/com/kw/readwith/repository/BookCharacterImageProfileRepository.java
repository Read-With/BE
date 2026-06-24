package com.kw.readwith.repository;

import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.BookCharacterImageProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BookCharacterImageProfileRepository extends JpaRepository<BookCharacterImageProfile, Long> {

    Optional<BookCharacterImageProfile> findByBook(Book book);
}
