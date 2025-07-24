package com.kw.readwith.domain;

import com.kw.readwith.domain.common.BaseEntity;
import com.kw.readwith.domain.enums.Provider;
import com.kw.readwith.domain.mapping.UserReadState;
import com.kw.readwith.domain.mapping.Favorite;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/* ─────────────── User ────────────────────────── */
@Entity
@Table(name = "users")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class User extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100, nullable = false, unique = true)
    private String email;

    @Column(length = 50, nullable = false)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Provider provider;

    @Column(name = "provider_uid", length = 120, nullable = false, unique = true)
    private String providerUid;

    @Column(name = "profile_img_url", length = 255)
    private String profileImgUrl;

    @Column(name = "jwt_refresh_token", columnDefinition = "CHAR(128)")
    private String jwtRefreshToken;

    /* 관계 : 업로드한 책, 읽기 상태 */
    @OneToMany(mappedBy = "uploadedBy", cascade = CascadeType.ALL)
    private List<Book> uploadedBooks = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<UserReadState> readStates = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<Favorite> favorites = new ArrayList<>();
}
