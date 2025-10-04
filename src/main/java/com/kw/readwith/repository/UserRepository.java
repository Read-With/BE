package com.kw.readwith.repository;

import com.kw.readwith.domain.User;
import com.kw.readwith.domain.enums.Provider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByProviderAndProviderUid(Provider provider, String providerUid);
    Optional<User> findByJwtRefreshToken(String refreshToken);
} 