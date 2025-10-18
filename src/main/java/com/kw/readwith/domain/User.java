package com.kw.readwith.domain;

import com.kw.readwith.domain.common.BaseEntity;
import com.kw.readwith.domain.enums.Provider;
import com.kw.readwith.domain.enums.Role;
import com.kw.readwith.domain.mapping.UserReadState;
import com.kw.readwith.domain.mapping.Favorite;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

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

    @Column(name = "is_admin", nullable = false)
    @ColumnDefault("false")
    private boolean isAdmin;

    @Column(name = "jwt_refresh_token", columnDefinition = "CHAR(128)")
    private String jwtRefreshToken;

    /* 관계 : 업로드한 책, 읽기 상태 */
    @OneToMany(mappedBy = "uploadedBy", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Book> uploadedBooks = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    @Builder.Default
    private List<UserReadState> readStates = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Favorite> favorites = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Bookmark> bookmarks = new ArrayList<>();

    /* 비즈니스 메서드 */
    public void updateJwtRefreshToken(String refreshToken) {
        this.jwtRefreshToken = refreshToken;
    }

    public void updateProfile(String nickname, String profileImgUrl) {
        if (nickname != null && !nickname.isBlank()) {
            this.nickname = nickname;
        }
        if (profileImgUrl != null && !profileImgUrl.isBlank()) {
            this.profileImgUrl = profileImgUrl;
        }
    }

    public Role getRole() {
        return this.isAdmin ? Role.ADMIN : Role.USER;
    }
}